package io.refrax.egress;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.ExposableProperty;
import io.refrax.projection.Projector;
import io.refrax.projection.ProjectorRegistry;
import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;
import io.refrax.view.MatchType;
import io.refrax.view.QueryAxis;
import io.refrax.view.View;
import io.refrax.view.ViewBinding;
import io.refrax.view.ViewBindingRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * View-driven read endpoints. Every read goes through a view (so no consumer can obtain more
 * than a view exposes) and is served from the read models, not by folding the raw log:
 *
 * <ul>
 *   <li>{@code /latest?{axis}=...} — current state per identity, from {@code reading_latest};</li>
 *   <li>{@code /stream?after={seq}} — ordered slice, from {@code reading_series};</li>
 *   <li>{@code /series?{axis}=...&from=&to=} — the valid-time window, from {@code reading_series}.</li>
 * </ul>
 *
 * The stored read model holds only values; role and vocabulary come from the schema when the
 * entity is reconstructed, and the view narrows which fields are reconstructed. A resolved
 * request carries a {@link ViewBinding} — a view already validated against its schema — so
 * nothing here re-checks that they match. An unknown view/format or an undeclared axis yields 400.
 */
@Path("v1/views")
public class ViewReadResource {

    private static final String DEFAULT_FORMAT = "native";
    private static final String FORMAT_PARAM = "format";
    private static final String FROM_PARAM = "from";
    private static final String TO_PARAM = "to";
    private static final int SLICE_LIMIT = 1000;

    @Inject
    io.vertx.mutiny.pgclient.PgPool client;

    @Inject
    ViewBindingRegistry bindings;

    @Inject
    ProjectorRegistry projectors;

    @GET
    @Path("{view}/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> latest(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        Resolved r = resolve(viewName, uriInfo.getQueryParameters().getFirst(FORMAT_PARAM));
        if (r.problem() != null) {
            return Uni.createFrom().item(r.problem());
        }
        try {
            SqlBuilder q = new SqlBuilder(
                    "select entity_id, exposed_json, observed_at from reading_latest where event_type = ")
                    .bind(r.binding().eventType());
            appendAxisFilters(r.binding(), uriInfo.getQueryParameters(), Set.of(FORMAT_PARAM), q);

            return client.preparedQuery(q.sql())
                    .execute(Tuple.from(q.params()))
                    .map(rows -> mapLatest(rows, r));
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(problem(e.getMessage()));
        }
    }

    private Response mapLatest(RowSet<Row> rows, Resolved r) {
        if (rows.rowCount() == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (rows.rowCount() > 1) {
            return problem("Axis filter matched multiple identities on /latest; narrow the filter or use /series");
        }
        Row row = rows.iterator().next();
        ExposableEntity entity = reconstruct(r.binding(),
                row.getString("entity_id"),
                (JsonObject) row.getValue("exposed_json"),
                row.getOffsetDateTime("observed_at"));
        return Response.ok(r.projector().project(entity)).build();
    }

    @GET
    @Path("{view}/stream")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> stream(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
        long after = parseLong(query.getFirst("after"), 0L);

        Resolved r = resolve(viewName, query.getFirst(FORMAT_PARAM));
        if (r.problem() != null) {
            return Uni.createFrom().item(r.problem());
        }

        return client.preparedQuery(
                        "select seq, entity_id, exposed_json, observed_at from reading_series "
                                + "where event_type = $1 and seq > $2 order by seq asc limit " + SLICE_LIMIT)
                .execute(Tuple.of(r.binding().eventType(), after))
                .map(rows -> Response.ok(sliceOf(rows, r)).build());
    }

    @GET
    @Path("{view}/series")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> series(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();

        Resolved r = resolve(viewName, query.getFirst(FORMAT_PARAM));
        if (r.problem() != null) {
            return Uni.createFrom().item(r.problem());
        }
        try {
            SqlBuilder q = new SqlBuilder(
                    "select seq, entity_id, exposed_json, observed_at from reading_series where event_type = ")
                    .bind(r.binding().eventType());
            appendAxisFilters(r.binding(), query, Set.of(FORMAT_PARAM, FROM_PARAM, TO_PARAM), q);

            String from = query.getFirst(FROM_PARAM);
            if (from != null) {
                q.sql(" and observed_at >= ").bind(parseTime(FROM_PARAM, from));
            }
            String to = query.getFirst(TO_PARAM);
            if (to != null) {
                q.sql(" and observed_at <= ").bind(parseTime(TO_PARAM, to));
            }
            q.sql(" order by observed_at asc, seq asc limit ").bind(SLICE_LIMIT);

            return client.preparedQuery(q.sql())
                    .execute(Tuple.from(q.params()))
                    .map(rows -> Response.ok(sliceOf(rows, r)).build());
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(problem(e.getMessage()));
        }
    }

    /** Appends the WHERE conditions from the declared query axes. Shared by /latest and /series. */
    private void appendAxisFilters(ViewBinding binding, MultivaluedMap<String, String> query,
                                   Set<String> reserved, SqlBuilder q) {
        for (Map.Entry<String, List<String>> entry : query.entrySet()) {
            String key = entry.getKey();
            if (reserved.contains(key)) {
                continue;
            }

            QueryAxis axis = binding.axis(key)
                    .orElseThrow(() -> new IllegalArgumentException("Undeclared query axis: " + key));
            if (axis.match() == MatchType.RANGE) {
                throw new IllegalArgumentException("Range axis '" + key + "' is not queryable directly; use from/to on /series");
            }

            String value = entry.getValue().getFirst();
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Query axis '" + key + "' requires a value");
            }

            if (binding.isIdentityField(axis.field())) {
                if (binding.identityFieldNames().size() > 1) {
                    throw new IllegalArgumentException("Cannot filter by a single component of a composite identity");
                }
                q.sql(" and entity_id = ").bind(binding.entityUrn(value));
            } else {
                q.sql(" and exposed_json ->> ").bind(axis.field()).sql(" = ").bind(value);
            }
        }
    }

    private JsonArray sliceOf(Iterable<Row> rows, Resolved r) {
        JsonArray slice = new JsonArray();
        for (Row row : rows) {
            ExposableEntity entity = reconstruct(r.binding(),
                    row.getString("entity_id"),
                    (JsonObject) row.getValue("exposed_json"),
                    row.getOffsetDateTime("observed_at"));
            slice.add(new JsonObject().put("seq", row.getLong("seq")).put("event", r.projector().project(entity)));
        }
        return slice;
    }

    /** Rebuilds the entity from stored values, narrowed to the view; role/vocabulary come from the schema. */
    private ExposableEntity reconstruct(ViewBinding binding, String entityId,
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
                        exposed.getValue(field.name()), field.role(), field.vocabularyUri(), field.personalData()));
            }
        }
        return new ExposableEntity(entityId, schema.eventType(), properties, observedAt);
    }

    private Resolved resolve(String viewName, String requestedFormat) {
        if (viewName == null || viewName.isBlank()) {
            return Resolved.failed(problem("View name parameter cannot be blank"));
        }
        ViewBinding binding = bindings.find(viewName).orElse(null);
        if (binding == null) {
            return Resolved.failed(problem("Unknown view: " + viewName));
        }
        String format = (requestedFormat == null || requestedFormat.isBlank()) ? DEFAULT_FORMAT : requestedFormat;
        Projector projector = projectors.byFormat(format).orElse(null);
        if (projector == null) {
            return Resolved.failed(problem("Unknown format: " + format));
        }
        return new Resolved(binding, projector, null);
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static OffsetDateTime parseTime(String param, String raw) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Parameter '" + param + "' is not a valid ISO-8601 timestamp: " + raw);
        }
    }

    private Response problem(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(new JsonObject().put("error", message)).build();
    }

    /** A resolved request: a validated view+schema binding plus the chosen projector. */
    private record Resolved(ViewBinding binding, Projector projector, Response problem) {
        static Resolved failed(Response problem) {
            return new Resolved(null, null, problem);
        }
    }
}
