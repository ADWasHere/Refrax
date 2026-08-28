package io.refrax.readmodel;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlClient;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Persistence for the read models and the consumer cursor. The consumer owns its own
 * schema (there is no migration tool yet), creating it idempotently, so it can be dropped
 * and rebuilt from the log at will. Two read models are maintained:
 *
 * <ul>
 *   <li>{@code reading_latest} — latest projection per identity (the current-state read);</li>
 *   <li>{@code reading_series} — every projection by valid-time (the time-series read). On a
 *       TimescaleDB deployment it is a hypertable (see {@code docker/initdb}); it carries no
 *       unique index excluding the partition column, so idempotency comes from the consumer's
 *       atomic (insert + cursor) transaction, not from {@code on conflict}.</li>
 * </ul>
 *
 * Write methods take a {@link SqlClient} so the consumer can run them inside one transaction.
 */
@ApplicationScoped
public class ReadModelStore {

    @Inject
    PgPool client;

    public Uni<Long> cursor(final String projection) {
        return client.preparedQuery("select position from projection_cursor where projection = $1")
                .execute(Tuple.of(projection))
                .map(rows -> rows.rowCount() == 0 ? 0L : rows.iterator().next().getLong("position"));
    }

    public Uni<Void> saveCursor(SqlClient exec, final String projection, final long position) {
        return exec.preparedQuery(
                        "insert into projection_cursor (projection, position) values ($1, $2) "
                                + "on conflict (projection) do update set position = excluded.position")
                .execute(Tuple.of(projection, position))
                .replaceWithVoid();
    }

    public Uni<Void> upsertLatest(SqlClient exec, List<LatestRow> rows) {
        if (rows.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<Tuple> tuples = rows.stream().map(r -> Tuple.of(
                r.eventType(), r.entityId(), r.exposedJson(), r.observedAt(), r.seq()
        )).toList();

        return exec.preparedQuery(
                "insert into reading_latest (event_type, entity_id, exposed_json, observed_at, seq) "
                        + "values ($1, $2, $3, $4, $5) "
                        + "on conflict (event_type, entity_id) do update set "
                        + "exposed_json = excluded.exposed_json, "
                        + "observed_at = excluded.observed_at, "
                        + "seq = excluded.seq "
                        + "where excluded.seq >= reading_latest.seq"
        ).executeBatch(tuples).replaceWithVoid();
    }

    public Uni<Void> insertSeries(SqlClient exec, List<SeriesRow> rows) {
        if (rows.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        List<Tuple> tuples = rows.stream().map(r -> Tuple.of(
                r.eventType(), r.seq(), r.entityId(), r.exposedJson(), r.observedAt()
        )).toList();

        return exec.preparedQuery(
                "insert into reading_series (event_type, seq, entity_id, exposed_json, observed_at) "
                        + "values ($1, $2, $3, $4, $5)"
        ).executeBatch(tuples).replaceWithVoid();
    }

    /** Wipes both read models and the cursor, so the next catch-up rebuilds from seq 0. */
    public Uni<Void> reset() {
        return client.query("truncate reading_latest; truncate reading_series; delete from projection_cursor")
                .execute()
                .replaceWithVoid();
    }
}
