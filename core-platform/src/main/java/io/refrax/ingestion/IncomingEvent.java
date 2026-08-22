package io.refrax.ingestion;

import java.time.Instant;
import java.util.Map;

public record IncomingEvent(String eventType,
                            Map<String, Object> fields,
                            Instant occurredAt) {
}
