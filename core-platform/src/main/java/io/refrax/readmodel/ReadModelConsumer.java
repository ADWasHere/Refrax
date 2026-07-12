package io.refrax.readmodel;

import io.quarkus.scheduler.Scheduled;
import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaRegistry;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The incremental read-model consumer. It reads the append-only log by {@code seq} from its
 * last cursor position and maintains the {@code reading_latest} and {@code reading_series}
 * read models, so reads fold nothing on demand. It is fully decoupled from ingest: the log
 * keeps accepting events while this lags or rebuilds, and it catches up afterwards.
 *
 * <p>Each batch's writes and its cursor advance commit in one transaction, so a crash never
 * duplicates or skips a row (exactly-once effect without any unique index — which is what the
 * time-series hypertable needs). Every event is projected through the {@link Gate} first, so
 * internal fields never reach a read model. Supports full replay (rebuild from seq 0) and
 * targeted replay (re-derive one entity) so a future erasure need not force a full rebuild.
 */
@ApplicationScoped
public class ReadModelConsumer {

    static final String CONSUMER = "latest+series";
    private static final int BATCH = 500;

    @Inject
    PgPool client;

    @Inject
    ReadModelStore store;

    @Inject
    SchemaRegistry schemas;

    @Inject
    Gate gate;

    /** Periodic catch-up. Skips if a run is still in flight, so it never overlaps itself. */
    // TODO: Change polling to postgres notify. For now till v1.0.0 good enough. Then use polling as backup (Maybe every 10-30s)
    @Scheduled(every = "3s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> tick() {
        return catchUp().replaceWithVoid();
    }

    /** Processes every event after the cursor, advancing it as it goes. Returns the new cursor. */
    public Uni<Long> catchUp() {
        return store.ensureSchema()
                .flatMap(v -> store.cursor(CONSUMER))
                .flatMap(this::drainFrom);
    }

    private Uni<Long> drainFrom(long from) {
        return client.withTransaction(conn -> conn.preparedQuery(
                                "select seq, event_type, payload, valid_time from events "
                                        + "where seq > $1 order by seq asc limit " + BATCH)
                        .execute(Tuple.of(from))
                        .emitOn(Infrastructure.getDefaultWorkerPool()) // To workerpool
                        .flatMap(rows -> {
                            if (rows.rowCount() == 0) {
                                return Uni.createFrom().item(new Batch(from, 0));
                            }

                            List<JournalEntry> entries = rows.stream()
                                    .map(JournalEntry::fromRow)
                                    .toList();

                            List<LatestRow> latest = new ArrayList<>();
                            List<SeriesRow> series = new ArrayList<>();

                            for (JournalEntry entry : entries) {
                                accumulate(entry, latest, series);
                            }

                            long cursor = entries.getLast().seq();
                            int count = entries.size();

                            return store.insertSeries(conn, series)
                                    .flatMap(v -> store.upsertLatest(conn, latest))
                                    .flatMap(v -> store.saveCursor(conn, CONSUMER, cursor))
                                    .replaceWith(new Batch(cursor, count));
                        }))
                .flatMap(batch -> batch.count() < BATCH
                        ? Uni.createFrom().item(batch.cursor())
                        : drainFrom(batch.cursor()));
    }

    /** Deletes both read models and the cursor, then rebuilds them from the log. */
    public Uni<Long> replayAll() {
        return store.ensureSchema()
                .flatMap(v -> store.reset())
                .flatMap(v -> catchUp());
    }

    /** Re-derives a single entity's current-state row from the log, touching nothing else. */
    public Uni<Void> reprojectEntity(String eventType, String identityField, String identityValue) {
        return store.ensureSchema().flatMap(v -> client.withTransaction(conn -> conn.preparedQuery(
                                "select seq, event_type, payload, valid_time from events "
                                        + "where event_type = $1 and payload ->> $2 = $3 order by seq desc limit 1")
                        .execute(Tuple.of(eventType, identityField, identityValue))
                        .emitOn(Infrastructure.getDefaultWorkerPool())
                        .flatMap(rows -> {
                            if (rows.rowCount() == 0) {
                                return Uni.createFrom().voidItem();
                            }

                            JournalEntry entry = JournalEntry.fromRow(rows.iterator().next());

                            List<LatestRow> latest = new ArrayList<>();
                            List<SeriesRow> series = new ArrayList<>();
                            accumulate(entry, latest, series);

                            return store.upsertLatest(conn, latest);
                        })));
    }

    private void accumulate(final JournalEntry entry, List<LatestRow> latest, List<SeriesRow> series) {
        String eventType = entry.eventType();
        EventSchema schema = schemas.find(eventType).orElse(null);
        if (schema == null) {
            return; // undeclared event type: cursor still advances, nothing materialised
        }
        JsonObject payload = entry.payload();
        OffsetDateTime validTime = entry.validTime();
        long seq = entry.seq();

        ExposableEntity entity = gate.project(schema, payload, validTime);
        JsonObject exposed = ReadModelProjection.exposedValues(entity);

        latest.add(LatestRow.builder()
                .eventType(eventType)
                .entityId(entity.id())
                .exposedJson(exposed)
                .observedAt(validTime)
                .seq(seq)
                .build());

        // The time-series read model is partitioned on valid-time, which must be present.
        if (validTime != null) {
            series.add(SeriesRow.builder()
                    .eventType(eventType)
                    .seq(seq)
                    .entityId(entity.id())
                    .exposedJson(exposed)
                    .observedAt(validTime)
                    .build());
        }
    }

    private record Batch(long cursor, int count) {
    }
}
