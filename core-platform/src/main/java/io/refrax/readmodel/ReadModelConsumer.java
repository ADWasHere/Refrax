package io.refrax.readmodel;

import io.quarkus.scheduler.Scheduled;
import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaRegistry;
import io.refrax.tenant.TenantContext;
import io.refrax.tenant.TenantRepository;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

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

    private static final Logger LOG = Logger.getLogger(ReadModelConsumer.class);

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

    @Inject
    TenantContext tenantContext;

    @Inject
    ReadModelMetrics metrics;

    /** Event-count lag above which a catch-up start is worth a WARN, not just an INFO. */
    @ConfigProperty(name = "refrax.readmodel.lag-warn-threshold", defaultValue = "1000")
    long lagWarnThreshold;

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

    /**
     * Current per-tenant lag: how many events past the cursor are not yet reflected in the read
     * models. The one thing an operator needs to answer "is the read model stale right now?"
     * without reading a log line or attaching a debugger.
     */
    public Uni<Long> lag() {
        return store.cursor(CONSUMER)
                .flatMap(cursor -> store.latestSeq().map(latestSeq -> Math.max(0, latestSeq - cursor)));
    }

    /**
     * Processes every event after the cursor, advancing it as it goes. Returns the new cursor.
     *
     * <p>Idle ticks (nothing to do) log nothing, on purpose — the scheduler fires every 3s
     * regardless of whether there is a backlog. A run that actually has something to process
     * logs its start (with the lag it found) and its completion (with how many events and how
     * long it took), so a catch-up after downtime is visible without reading source code.
     */
    public Uni<Long> catchUp() {
        return store.cursor(CONSUMER).flatMap(from -> store.latestSeq().flatMap(latestSeq -> {
            long lag = Math.max(0, latestSeq - from);
            metrics.recordLag(tenantContext.getTenantId(), lag);
            if (lag == 0) {
                return Uni.createFrom().item(from);
            }

            if (lag > lagWarnThreshold) {
                LOG.warnf("Read-model consumer is %d event(s) behind (threshold %d); cursor=%d, latestSeq=%d",
                        lag, lagWarnThreshold, from, latestSeq);
            }
            LOG.infof("Catch-up started: %d event(s) behind", lag);
            long startNanos = System.nanoTime();

            return drainFrom(from, 0).invoke(result -> {
                long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
                LOG.infof("Catch-up completed: processed %d event(s) in %d ms, cursor now at seq=%d",
                        result.count(), tookMs, result.cursor());
            }).map(Run::cursor);
        }));
    }

    /** Drains the log from a given seq, in batches, until it reaches the end. Returns the new cursor
     * and the total events processed across every batch of this run.
     * Is recursive, but each batch is a new transaction and on a worker thread, so it won't blow the stack.
     */
    private Uni<Run> drainFrom(long from, long processedSoFar) {
        return store.readEventsAfter(from, BATCH)
                .flatMap(entries -> {
                    if (entries.isEmpty()) {
                        return Uni.createFrom().item(new Run(from, processedSoFar));
                    }

                    List<LatestRow> latest = new ArrayList<>();
                    List<SeriesRow> series = new ArrayList<>();

                    for (JournalEntry entry : entries) {
                        accumulate(entry, latest, series);
                    }

                    long cursor = entries.getLast().seq();
                    int count = entries.size();
                    long total = processedSoFar + count;

                    return store.insertSeries(series)
                            .flatMap(v -> store.upsertLatest(latest))
                            .flatMap(v -> store.saveCursor(CONSUMER, cursor))
                            .invoke(() -> LOG.debugf("Caught up %d event(s) in this batch, cursor now at seq=%d", count, cursor))
                            .replaceWith(new Run(cursor, total))
                            .flatMap(run -> count < BATCH
                                    ? Uni.createFrom().item(run)
                                    : drainFrom(run.cursor(), run.count()));
                });
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
        MDC.put("event.type", entry.eventType());
        MDC.put("event.id", String.valueOf(entry.eventId()));
        try {
            String eventType = entry.eventType();
            EventSchema schema = schemas.find(eventType).orElse(null);
            if (schema == null) {
                LOG.warnf("Skipping event with undeclared event type '%s' (seq=%d); cursor still advances", eventType, entry.seq());
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
            LOG.debugf("Projected event into read model, seq=%d", seq);
        } finally {
            MDC.remove("event.type");
            MDC.remove("event.id");
        }
    }

    /** A catch-up run's progress: the cursor it has reached and events processed so far. */
    private record Run(long cursor, long count) {
    }
}
