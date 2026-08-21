package io.refrax.egress;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/**
 * Orchestrates view reads against the read model.
 *
 * <p>The service is intentionally slim: it resolves the requested view, builds the SQL statement from
 * the schema-aware filter parser, executes the query, and delegates the materialization step to the
 * mapper component.
 */
@ApplicationScoped
public class ViewQueryService {

    private static final String FORMAT_PARAM = "format";
    private static final String FROM_PARAM = "from";
    private static final String TO_PARAM = "to";
    private static final int SLICE_LIMIT = 1000;

    @Inject
    PgPool client;

    @Inject
    ViewResolver viewResolver;

    @Inject
    AxisFilterParser axisFilterParser;

    @Inject
    ViewEntityMapper viewEntityMapper;

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

        return client.preparedQuery(q.sql())
                .execute(Tuple.from(q.params()))
                .map(rows -> viewEntityMapper.mapLatest(rows, r));
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

        return client.preparedQuery(
                        "select seq, entity_id, exposed_json, observed_at from reading_series "
                                + "where event_type = $1 and seq > $2 order by seq asc limit " + SLICE_LIMIT)
                .execute(Tuple.of(r.binding().eventType(), after))
                .map(rows -> Response.ok(viewEntityMapper.sliceOf(rows, r)).build());
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

        return client.preparedQuery(q.sql())
                .execute(Tuple.from(q.params()))
                .map(rows -> Response.ok(viewEntityMapper.sliceOf(rows, r)).build());
    }
}
