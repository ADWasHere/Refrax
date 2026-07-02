package io.refrax.egress;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.projection.Projector;
import io.refrax.projection.ProjectorRegistry;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaRegistry;
import io.refrax.view.MatchType;
import io.refrax.view.QueryAxis;
import io.refrax.view.View;
import io.refrax.view.ViewRegistry;
import io.refrax.view.ViewValidator;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * View-driven read endpoints. Every read goes through a view, so no consumer can bypass the
 * view/gate layer to obtain more than a view exposes:
 *
 * <ul>
 *   <li>{@code GET /v1/views/{view}/latest?{axis}=...&format=...} — the latest matching
 *       event, narrowed to what the view exposes;</li>
 *   <li>{@code GET /v1/views/{view}/stream?after={seq}&format=...} — the ordered log slice
 *       for that view, no identity assumed, each event narrowed the same way.</li>
 * </ul>
 *
 * The event type and exposed fields come from the view, the queryable axes from the view,
 * the shape from the projector (default {@code native}). An unknown view or format, or a
 * query on an undeclared axis, yields 400.
 */
@Path("v1/views")
public class ViewReadResource {

    private static final String DEFAULT_FORMAT = "native";
    private static final String FORMAT_PARAM = "format";
    private static final int SLICE_LIMIT = 1000;

    @Inject
    io.vertx.mutiny.pgclient.PgPool client;

    @Inject
    SchemaRegistry schemas;

    @Inject
    ViewRegistry views;

    @Inject
    Gate gate;

    @Inject
    ProjectorRegistry projectors;

    @GET
    @Path("{view}/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> latest(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        View view = views.find(viewName).orElse(null);
        if (view == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST, "Unknown view: " + viewName));
        }
        EventSchema schema = schemas.find(view.eventType()).orElse(null);
        if (schema == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST,
                    "View '" + viewName + "' is over unknown event type '" + view.eventType() + "'"));
        }

        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();

        Projector projector = projectors.byFormat(formatOf(query.getFirst(FORMAT_PARAM))).orElse(null);
        if (projector == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST,
                    "Unknown format: " + query.getFirst(FORMAT_PARAM)));
        }

        StringBuilder sql = new StringBuilder(
                "select payload, valid_time from events where event_type = $1");
        List<Object> params = new ArrayList<>();
        params.add(view.eventType());
        int p = 2;

        for (Map.Entry<String, List<String>> entry : query.entrySet()) {
            String key = entry.getKey();
            if (key.equals(FORMAT_PARAM)) {
                continue;
            }
            QueryAxis axis = view.axis(key).orElse(null);
            if (axis == null) {
                return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST,
                        "Undeclared query axis: " + key));
            }
            String value = entry.getValue().get(0);
            if (axis.match() == MatchType.RANGE && ViewValidator.VALID_TIME.equals(axis.field())) {
                // "as of" upper bound on valid-time.
                sql.append(" and valid_time <= $").append(p++).append("::timestamptz");
                params.add(value);
            } else {
                sql.append(" and payload ->> $").append(p++).append(" = $").append(p++);
                params.add(axis.field());
                params.add(value);
            }
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

                    ExposableEntity entity = gate.project(schema, view, payload, validTime);

                    return Response.ok(selected.project(entity)).build();
                });
    }

    @GET
    @Path("{view}/stream")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> stream(@PathParam("view") String viewName,
                                @QueryParam("after") @DefaultValue("0") long after,
                                @QueryParam(FORMAT_PARAM) String format) {
        View view = views.find(viewName).orElse(null);
        if (view == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST, "Unknown view: " + viewName));
        }
        EventSchema schema = schemas.find(view.eventType()).orElse(null);
        if (schema == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST,
                    "View '" + viewName + "' is over unknown event type '" + view.eventType() + "'"));
        }
        Projector projector = projectors.byFormat(formatOf(format)).orElse(null);
        if (projector == null) {
            return Uni.createFrom().item(problem(Response.Status.BAD_REQUEST, "Unknown format: " + format));
        }

        Projector selected = projector;
        return client.preparedQuery(
                        "select seq, payload, valid_time from events "
                                + "where event_type = $1 and seq > $2 order by seq asc limit " + SLICE_LIMIT)
                .execute(Tuple.of(view.eventType(), after))
                .map(rows -> {
                    JsonArray slice = new JsonArray();
                    for (Row row : rows) {
                        long seq = row.getLong("seq");
                        JsonObject payload = (JsonObject) row.getValue("payload");
                        OffsetDateTime validTime = row.getOffsetDateTime("valid_time");

                        ExposableEntity entity = gate.project(schema, view, payload, validTime);
                        slice.add(new JsonObject().put("seq", seq).put("event", selected.project(entity)));
                    }
                    return Response.ok(slice).build();
                });
    }

    private static String formatOf(String requested) {
        return (requested == null || requested.isBlank()) ? DEFAULT_FORMAT : requested;
    }

    private Response problem(Response.Status status, String message) {
        return Response.status(status).entity(new JsonObject().put("error", message)).build();
    }
}
