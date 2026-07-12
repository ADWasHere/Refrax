package io.refrax.readmodel;

import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;

import java.time.OffsetDateTime;

public record JournalEntry(
        long seq,
        String eventType,
        JsonObject payload,
        OffsetDateTime validTime
) {
    public static JournalEntry fromRow(Row row) {
        return new JournalEntry(
                row.getLong("seq"),
                row.getString("event_type"),
                (JsonObject) row.getValue("payload"),
                row.getOffsetDateTime("valid_time")
        );
    }
}
