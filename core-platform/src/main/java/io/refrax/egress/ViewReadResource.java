package io.refrax.egress;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.projection.Projector;
import io.refrax.projection.ProjectorRegistry;
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
 * Identity-driven read endpoint. {@code GET /v1/views/{view}/latest?{identity}=...} returns
 * the latest event of the given type projected through the gate, rendered by the projector
 * named in {@code ?format=} (default {@code native}). Nothing is hardcoded: the event type
 * comes from the path, the identity field name(s) from the schema, and the shape from the
 * selected projector. An unknown view or format, or a missing identity parameter, yields
 * 400.
 */
@Path("v1/views")
public class ViewReadResource {

    private static final String DEFAULT_FORMAT = "native";

    @Inject
    io.vertx.mutiny.pgclient.PgPool client;

    @Inject
    SchemaRegistry registry;

    @Inject
    Gate gate;

    @Inject
    ProjectorRegistry projectors;

    @GET
    @Path("{view}/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> latest(@PathParam("view") String view, @Context UriInfo uriInfo) {
        EventSchema schema = registry.find(view).orElse(null);
        if (schema == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST, "Unknown view: " + view));
        }

        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();

        String format = query.getFirst("format");
        if (format == null || format.isBlank()) {
            format = DEFAULT_FORMAT;
        }
        Projector projector = projectors.byFormat(format).orElse(null);
        if (projector == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST, "Unknown format: " + format));
        }

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

        Projector selected = projector;
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

                    return Response.ok(selected.project(entity)).build();
                });
    }

    private Response problem(Response.Status status, String message) {
        return Response.status(status).entity(new JsonObject().put("error", message)).build();
    }
}
