package io.refrax.readmodel;

import io.refrax.ingestion.Events;
import io.vertx.core.json.JsonObject;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JournalEntry(
        long seq,
        String eventType,
        JsonObject payload,
        OffsetDateTime validTime,
        UUID eventId
) {

    public static JournalEntry fromEntity(Events event) {
        return new JournalEntry(
                event.getSeq(),
                event.getEventType(),
                new JsonObject(event.getPayload()),
                event.getValidTime(),
                event.getEventId()
        );
    }
}
