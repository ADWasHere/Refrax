package io.refrax.egress;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.SchemaRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Identity-driven native read endpoint.
 *
 * <p>{@code GET /v1/views/{view}/latest?{identity}=...} returns the latest event of the
 * given type projected through the gate. A {@code {view}} that names no known schema
 * yields 400; a missing identity query parameter yields 400.
 */
@Path("v1/views")
public class ViewReadResource {

    @Inject
    io.vertx.mutiny.pgclient.PgPool client;

    @Inject
    SchemaRegistry registry;

    @Inject
    Gate gate;

    @GET
    @Path("{view}/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> latest(@PathParam("view") String view, @Context UriInfo uriInfo) {
        EventSchema schema = registry.find(view).orElse(null);

        if (schema == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST, "Unknown view: " + view));
        }

        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
        StringBuilder sql = new StringBuilder(
                "select payload, valid_time from events where event_type = $1");
        List<Object> params = new ArrayList<>();
        params.add(view);
        int p = 2;
        for (FieldDeclaration id : schema.identityComponents()) {
            String value = query.getFirst(id.name());
            if (value == null || value.isBlank()) {
                return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST,
                        "Missing identity query parameter: " + id.name()));
            }
            sql.append(" and payload ->> $").append(p++).append(" = $").append(p++);
            params.add(id.name());
            params.add(value);
        }
        sql.append(" order by seq desc limit 1");

        return client.preparedQuery(sql.toString())
                .execute(Tuple.from(params))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    Row row = rows.iterator().next();
                    JsonObject payload = (JsonObject) row.getValue("payload");
                    OffsetDateTime validTime = row.getOffsetDateTime("valid_time");

                    ExposableEntity entity = gate.project(schema, payload, validTime);

                    return Response.ok(renderNative(entity)).build();
                });
    }

    /** Native projection: the {@link ExposableEntity} rendered directly. */
    private JsonObject renderNative(ExposableEntity entity) {
        JsonObject out = new JsonObject();
        out.put("id", entity.id());
        out.put("type", entity.type());
        if (entity.observedAt() != null) {
            out.put("observedAt", entity.observedAt().toString());
        }
        entity.properties().forEach((name, prop) -> out.put(name, prop.value()));
        return out;
    }

    private Response problem(Response.Status status, String message) {
        return Response.status(status).entity(new JsonObject().put("error", message)).build();
    }
}
