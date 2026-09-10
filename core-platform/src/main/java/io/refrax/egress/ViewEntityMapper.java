package io.refrax.egress;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.ExposableProperty;
import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;
import io.refrax.view.View;
import io.refrax.view.ViewBinding;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps database rows back into the final JAX-RS response objects used by the view endpoints.
 *
 * <p>The mapper is responsible for reconstructing the exposable entity from stored JSONB data and for
 * converting the results of the latest and series queries into either a single response or a JSON array.
 */
@ApplicationScoped
public class ViewEntityMapper {

    /**
     * Maps a latest-query result set to a single JAX-RS response.
     *
     * @param rows the database rows returned by the latest query, each {@code {entity_id, exposed_json, observed_at}}
     * @param r the resolved view binding and projector
     * @return the HTTP response for the latest view result
     */
    public Response mapLatest(List<Object[]> rows, ViewResolver.Resolved r) {
        if (rows.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (rows.size() > 1) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JsonObject().put("error",
                            "Axis filter matched multiple identities on /latest; narrow the filter or use /series"))
                    .build();
        }

        Object[] row = rows.get(0);
        ExposableEntity entity = reconstruct(r.binding(),
                (String) row[0],
                asJson(row[1]),
                (OffsetDateTime) row[2]);
        return Response.ok(r.projector().project(entity)).build();
    }

    /**
     * Maps a time-series result set to a JSON array of events.
     *
     * @param rows the database rows returned by the series query, each {@code {seq, entity_id, exposed_json, observed_at}}
     * @param r the resolved view binding and projector
     * @return the serialized series response payload
     */
    public JsonArray sliceOf(List<Object[]> rows, ViewResolver.Resolved r) {
        JsonArray slice = new JsonArray();
        for (Object[] row : rows) {
            ExposableEntity entity = reconstruct(r.binding(),
                    (String) row[1],
                    asJson(row[2]),
                    (OffsetDateTime) row[3]);
            slice.add(new JsonObject()
                    .put("seq", (Long) row[0])
                    .put("event", r.projector().project(entity)));
        }
        return slice;
    }

    private static JsonObject asJson(Object value) {
        return value instanceof JsonObject json ? json : new JsonObject((String) value);
    }

    /**
     * Rebuilds an exposable entity from the stored JSONB payload, filtered to the currently resolved view.
     *
     * @param binding the validated view/schema binding
     * @param entityId the entity identifier
     * @param exposed the stored JSONB payload
     * @param observedAt the observed timestamp for the row
     * @return the reconstructed entity
     */
    public ExposableEntity reconstruct(ViewBinding binding, String entityId,
                                      JsonObject exposed, OffsetDateTime observedAt) {
        EventSchema schema = binding.schema();
        View view = binding.view();
        Map<String, ExposableProperty> properties = new LinkedHashMap<>();
        for (FieldDeclaration field : schema.exposableFields()) {
            if (field.role() == FieldRole.IDENTITY || !view.exposesField(field.name())) {
                continue;
            }
            if (exposed.containsKey(field.name())) {
                properties.put(field.name(), new ExposableProperty(
                        exposed.getValue(field.name()),
                        field.role(),
                        field.vocabularyUri(),
                        field.personalData()));
            }
        }
        return new ExposableEntity(entityId, schema.eventType(), properties, observedAt);
    }
}
