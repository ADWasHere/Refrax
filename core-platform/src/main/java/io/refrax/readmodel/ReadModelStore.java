package io.refrax.readmodel;

import io.refrax.ingestion.Events;
import io.refrax.projection.ProjectionCursor;
import io.refrax.tenant.TenantAwarePanache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Persistence for the read models and the consumer cursor. The schema is owned by Flyway
 * (see {@code db/migration}); the read models remain disposable and can be wiped and rebuilt
 * from the log at will (see {@link #reset()}). Two read models are maintained:
 *
 * <ul>
 *   <li>{@code reading_latest} — latest projection per identity (the current-state read);</li>
 *   <li>{@code reading_series} — every projection by valid-time (the time-series read). On a
 *       TimescaleDB deployment it is a hypertable; it carries no unique index excluding the
 *       partition column, so idempotency comes from the consumer's atomic (insert + cursor)
 *       transaction, not from {@code on conflict}.</li>
 * </ul>
 */
@ApplicationScoped
public class ReadModelStore {
    @Inject
    TenantAwarePanache panache;

    public Uni<Long> cursor(final String projection) {
        return panache.withTransaction(() -> ProjectionCursor.findByProjection(projection)
                .map(cursor -> cursor == null ? 0L : cursor.getPosition()));
    }

    public Uni<Void> saveCursor(final String projection, final long position) {
        return panache.withTransaction(() -> ProjectionCursor.findByProjection(projection)
                .flatMap(cursor -> {
                    if (cursor == null) {
                        cursor = new ProjectionCursor();
                        cursor.setProjection(projection);
                    }
                    cursor.setPosition(position);
                    return cursor.persistAndFlush().replaceWithVoid();
                }));
    }

    public Uni<Void> upsertLatest(List<LatestRow> rows) {
        if (rows.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return panache.withTransaction(() -> Multi.createFrom().iterable(rows)
                .onItem().transformToUni(row -> {
                    ReadingLatestId id = new ReadingLatestId(row.eventType(), row.entityId());
                    return ReadingLatest.<ReadingLatest>find("id = ?1", id).firstResult()
                            .flatMap(latest -> {
                                ReadingLatest latestEntity = latest == null ? new ReadingLatest() : latest;
                                if (latest == null) {
                                    latestEntity.setId(id);
                                }
                                if (latestEntity.getSeq() == null || row.seq() >= latestEntity.getSeq()) {
                                    latestEntity.setExposedJson(row.exposedJson().encode());
                                    latestEntity.setObservedAt(row.observedAt());
                                    latestEntity.setSeq(row.seq());
                                    return latestEntity.persistAndFlush().replaceWithVoid();
                                }
                                return Uni.createFrom().voidItem();
                            });
                })
                .concatenate()
                .collect().asList()
                .replaceWithVoid());
    }

    public Uni<Void> insertSeries(List<SeriesRow> rows) {
        if (rows.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return panache.withTransaction(() -> Multi.createFrom().iterable(rows)
                .onItem().transformToUni(row -> {
                    ReadingSeriesId id = new ReadingSeriesId(row.eventType(), row.seq(), row.observedAt());
                    return ReadingSeries.<ReadingSeries>find("id = ?1", id).firstResult()
                            .flatMap(entity -> {
                                ReadingSeries entityValue = entity == null ? new ReadingSeries() : entity;
                                if (entity == null) {
                                    entityValue.setId(id);
                                    entityValue.setEntityId(row.entityId());
                                    entityValue.setExposedJson(row.exposedJson().encode());
                                    return entityValue.persistAndFlush().replaceWithVoid();
                                }
                                return Uni.createFrom().voidItem();
                            });
                })
                .concatenate()
                .collect().asList()
                .replaceWithVoid());
    }

    public Uni<List<JournalEntry>> readEventsAfter(long from, int limit) {
        return panache.withTransaction(() -> Events.<Events>find("seq > ?1 order by seq asc", from)
                .page(0, limit)
                .list()
                .map(rows -> rows.stream().map(JournalEntry::fromEntity).toList()));
    }

    public Uni<JournalEntry> findLatestEvent(String eventType, String identityField, String identityValue) {
        return panache.withTransaction(() -> Events.<Events>find("eventType = ?1 order by seq desc", eventType)
                .list()
                .map(rows -> {
                    for (Events row : rows) {
                        JsonObject payload = new JsonObject(row.getPayload());
                        String actual = payload.getString(identityField);
                        if (identityValue.equals(actual)) {
                            return JournalEntry.fromEntity(row);
                        }
                    }
                    return null;
                }));
    }

    /** Wipes both read models and the cursor, so the next catch-up rebuilds from seq 0. */
    public Uni<Void> reset() {
        return panache.withTransaction(() -> ProjectionCursor.deleteAll()
                .flatMap(ignored -> ReadingLatest.deleteAll())
                .flatMap(ignored -> ReadingSeries.deleteAll())
                .replaceWithVoid());
    }
}
