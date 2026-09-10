package io.refrax.egress;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.refrax.tenant.TenantAwarePanache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Orchestrates view reads against the read model.
 *
 * <p>The service is intentionally slim: it resolves the requested view, builds the SQL statement from
 * the schema-aware filter parser, executes the query, and delegates the materialization step to the
 * mapper component.
 *
 * <p>Queries run as Hibernate Reactive native queries (not the raw {@code PgPool}), because
 * {@link TenantAwarePanache} pins the tenant's schema with {@code SET LOCAL search_path} on the
 * connection borrowed through the Hibernate Reactive session — a query on a separately-borrowed raw
 * pool connection would not see that pin at all.
 */
@ApplicationScoped
public class ViewQueryService {

    private static final String FORMAT_PARAM = "format";
    private static final String FROM_PARAM = "from";
    private static final String TO_PARAM = "to";
    private static final int SLICE_LIMIT = 1000;

    @Inject
    ViewResolver viewResolver;

    @Inject
    AxisFilterParser axisFilterParser;

    @Inject
    ViewEntityMapper viewEntityMapper;

    @Inject
    TenantAwarePanache panache;

    /**
     * Fetches the current latest state for a view and optional axis filters.
     *
     * @param viewName the view name to query
     * @param query the raw request query parameters
     * @return the latest entity response wrapped in a reactive {@link Uni}
     */
    public Uni<Response> latest(String viewName, MultivaluedMap<String, String> query) {
        ViewResolver.Resolved r = viewResolver.resolve(viewName, query.getFirst(FORMAT_PARAM));

        SqlBuilder q = new SqlBuilder(
                "select entity_id, exposed_json, observed_at from reading_latest where event_type = ")
                .bind(r.binding().eventType());
        axisFilterParser.appendAxisFilters(r.binding(), query, java.util.Set.of(FORMAT_PARAM), q);

        return panache.withTransaction(() -> executeQuery(q)
                .map(rows -> viewEntityMapper.mapLatest(rows, r)));
    }

    /**
     * Streams a bounded slice of series rows after the given sequence number.
     *
     * @param viewName the view name to query
     * @param requestedFormat the requested output format
     * @param after the sequence offset to start after
     * @return the streaming slice response wrapped in a reactive {@link Uni}
     */
    public Uni<Response> stream(String viewName, String requestedFormat, long after) {
        ViewResolver.Resolved r = viewResolver.resolve(viewName, requestedFormat);

        SqlBuilder q = new SqlBuilder(
                "select seq, entity_id, exposed_json, observed_at from reading_series where event_type = ")
                .bind(r.binding().eventType())
                .sql(" and seq > ").bind(after)
                .sql(" order by seq asc limit " + SLICE_LIMIT);

        return panache.withTransaction(() -> executeQuery(q)
                .map(rows -> Response.ok(viewEntityMapper.sliceOf(rows, r)).build()));
    }

    /**
     * Fetches a filtered time-series slice of events for the given view.
     *
     * @param viewName the view name to query
     * @param query the raw request query parameters
     * @return the time-series response wrapped in a reactive {@link Uni}
     */
    public Uni<Response> series(String viewName, MultivaluedMap<String, String> query) {
        ViewResolver.Resolved r = viewResolver.resolve(viewName, query.getFirst(FORMAT_PARAM));

        SqlBuilder q = new SqlBuilder(
                "select seq, entity_id, exposed_json, observed_at from reading_series where event_type = ")
                .bind(r.binding().eventType());
        axisFilterParser.appendAxisFilters(r.binding(), query, java.util.Set.of(FORMAT_PARAM, FROM_PARAM, TO_PARAM), q);
        axisFilterParser.appendTimeFilters(query, q);
        q.sql(" order by observed_at asc, seq asc limit ").bind(SLICE_LIMIT);

        return panache.withTransaction(() -> executeQuery(q)
                .map(rows -> Response.ok(viewEntityMapper.sliceOf(rows, r)).build()));
    }

    /** Executes a dynamically-built SQL statement as a Hibernate Reactive native query. */
    private Uni<List<Object[]>> executeQuery(SqlBuilder q) {
        return Panache.getSession().flatMap(session -> {
            var query = session.createNativeQuery(q.sql(), Object[].class);
            List<Object> params = q.params();
            for (int i = 0; i < params.size(); i++) {
                query = query.setParameter(i + 1, params.get(i));
            }
            return query.getResultList();
        });
    }
}
