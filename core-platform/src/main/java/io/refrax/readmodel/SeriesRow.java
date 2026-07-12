package io.refrax.readmodel;

import io.vertx.core.json.JsonObject;
import java.time.OffsetDateTime;
import java.util.Objects;

public record SeriesRow(
        String eventType,
        long seq,
        String entityId,
        JsonObject exposedJson,
        OffsetDateTime observedAt
) {
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        private String eventType;
        private long seq;
        private String entityId;
        private JsonObject exposedJson;
        private OffsetDateTime observedAt;

        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder seq(long seq) { this.seq = seq; return this; }
        public Builder entityId(String entityId) { this.entityId = entityId; return this; }
        public Builder exposedJson(JsonObject exposedJson) { this.exposedJson = exposedJson; return this; }
        public Builder observedAt(OffsetDateTime observedAt) { this.observedAt = observedAt; return this; }

        public SeriesRow build() {
            Objects.requireNonNull(eventType, "eventType must not be null");
            Objects.requireNonNull(entityId, "entityId must not be null");
            Objects.requireNonNull(exposedJson, "exposedJson must not be null");
            Objects.requireNonNull(observedAt, "observedAt must not be null");

            return new SeriesRow(eventType, seq, entityId, exposedJson, observedAt);
        }
    }
}
