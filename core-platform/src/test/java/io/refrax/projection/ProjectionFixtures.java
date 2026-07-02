package io.refrax.projection;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaLoader;
import io.vertx.core.json.JsonObject;

import java.time.OffsetDateTime;

/**
 * Builds {@link ExposableEntity} instances through the real schema + gate, so projector
 * tests exercise the same path an ingested event would take, minus HTTP and the database.
 */
final class ProjectionFixtures {

    static final EventSchema SCHEMA = SchemaLoader.load("schemas/air-quality-reading.json");
    static final OffsetDateTime OBSERVED_AT = OffsetDateTime.parse("2026-07-02T10:00:00Z");

    private static final Gate GATE = new Gate();

    private ProjectionFixtures() {
    }

    /** A clean reading: only the declared exposable fields. */
    static ExposableEntity cleanReading(String sensorId) {
        JsonObject payload = new JsonObject()
                .put("sensorId", sensorId)
                .put("metric", "PM2.5")
                .put("value", 12.3)
                .put("unit", "ug/m3");
        return GATE.project(SCHEMA, payload, OBSERVED_AT);
    }

    /** A reading whose payload is polluted with internal fields the gate must drop. */
    static ExposableEntity pollutedReading(String sensorId) {
        JsonObject payload = new JsonObject()
                .put("sensorId", sensorId)
                .put("metric", "PM2.5")
                .put("value", 14.7)
                .put("unit", "ug/m3")
                .put("deviceDbId", 999999)
                .put("userId", "u-123")
                .put("partitionKey", "shard-7");
        return GATE.project(SCHEMA, payload, OBSERVED_AT);
    }
}
