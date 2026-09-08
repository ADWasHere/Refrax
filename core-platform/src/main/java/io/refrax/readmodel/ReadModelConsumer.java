package io.refrax.readmodel;

import io.quarkus.scheduler.Scheduled;
import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaRegistry;
import io.refrax.tenant.TenantRepository;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
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
    ReadModelStore store;

    @Inject
    SchemaRegistry schemas;

    @Inject
    Gate gate;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    TenantJobRunner tenantJobRunner;

    /**
     * Periodic catch-up. Skips if a run is still in flight, so it never overlaps itself.
     * <br/><br/>
     * Each tenant runs on its own brand-new Vert.x duplicated context ({@link #runIsolated})
     */
    // TODO: Change polling to postgres notify. For now till v1.0.0 good enough. Then use polling as backup (Maybe every 10-30s)
    @Scheduled(every = "3s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> tick() {
        return tenantRepository.findAllTenantSchemas()
                .flatMap(schemas -> Multi.createFrom().iterable(schemas)
                        .onItem().transformToUni(this::runIsolated).concatenate()
                        .collect().asList()
                        .replaceWithVoid()
                );
    }

    private Uni<Void> runIsolated(String schema) {
        Context freshContext = VertxContext.createNewDuplicatedContext();
        return Uni.createFrom().emitter(emitter ->
                freshContext.runOnContext(ignored -> tenantJobRunner.runForTenant(schema)
                        .subscribe().with(emitter::complete, emitter::fail)));
    }

    /** Processes every event after the cursor, advancing it as it goes. Returns the new cursor. */
    public Uni<Long> catchUp() {
        return store.cursor(CONSUMER)
                .flatMap(this::drainFrom);
    }

    /** Drains the log from a given seq, in batches, until it reaches the end. Returns the new cursor.
     * Is recursive, but each batch is a new transaction and on a worker thread, so it won't blow the stack.
     */
    private Uni<Long> drainFrom(long from) {
        return store.readEventsAfter(from, BATCH)
                .flatMap(entries -> {
                    if (entries.isEmpty()) {
                        return Uni.createFrom().item(new Batch(from, 0));
                    }

                    List<LatestRow> latest = new ArrayList<>();
                    List<SeriesRow> series = new ArrayList<>();

                    for (JournalEntry entry : entries) {
                        accumulate(entry, latest, series);
                    }

                    long cursor = entries.getLast().seq();
                    int count = entries.size();

                    return store.insertSeries(series)
                            .flatMap(v -> store.upsertLatest(latest))
                            .flatMap(v -> store.saveCursor(CONSUMER, cursor))
                            .replaceWith(new Batch(cursor, count));
                })
                .flatMap(batch -> batch.count() < BATCH
                        ? Uni.createFrom().item(batch.cursor())
                        : drainFrom(batch.cursor()));
    }

    /** Deletes both read models and the cursor, then rebuilds them from the log. */
    public Uni<Long> replayAll() {
        return store.reset()
                .flatMap(v -> catchUp());
    }

    /** Re-derives a single entity's current-state row from the log, touching nothing else. */
    public Uni<Void> reprojectEntity(String eventType, String identityField, String identityValue) {
        return store.findLatestEvent(eventType, identityField, identityValue)
                .flatMap(entry -> {
                    if (entry == null) {
                        return Uni.createFrom().voidItem();
                    }

                    List<LatestRow> latest = new ArrayList<>();
                    List<SeriesRow> series = new ArrayList<>();
                    accumulate(entry, latest, series);

                    return store.upsertLatest(latest);
                });
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
