package io.refrax.view;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.Gate;
import io.refrax.projection.NativeProjector;
import io.refrax.projection.NgsiLdProjector;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaLoader;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The view is applied when the {@link ExposableEntity} is built, before any projector, so a
 * view shrinks the entity for every format at once (the view × format matrix) and the leak
 * guarantee is inherited unchanged. Pure unit test: gate + view + projectors, no DB/HTTP.
 */
class ViewProjectionTest {

    private static final EventSchema SCHEMA = SchemaLoader.load("schemas/air-quality-reading.json");
    private static final View VALUE_ONLY = ViewLoader.load("views/air-quality-value-only.json");
    private static final OffsetDateTime OBSERVED_AT = OffsetDateTime.parse("2026-07-02T10:05:00Z");

    private final Gate gate = new Gate();
    private final NativeProjector nativeProjector = new NativeProjector();
    private final NgsiLdProjector ngsiLdProjector = new NgsiLdProjector();

    private static JsonObject pollutedPayload() {
        return new JsonObject()
                .put("sensorId", "sensor-42")
                .put("metric", "PM2.5")
                .put("value", 14.7)
                .put("unit", "ug/m3")
                .put("deviceDbId", 999999)
                .put("userId", "u-123")
                .put("partitionKey", "shard-7");
    }

    @Test
    void viewShrinksUnderSchemaInNative() {
        ExposableEntity entity = gate.project(SCHEMA, VALUE_ONLY, pollutedPayload(), OBSERVED_AT);
        JsonObject doc = nativeProjector.project(entity);

        assertEquals(14.7, doc.getDouble("value"));
        assertEquals("ug/m3", doc.getString("unit"));
        // metric is schema-exposable but not in this view.
        assertNull(doc.getValue("metric"));
    }

    @Test
    void sameViewYieldsSameSubsetInEveryFormat() {
        ExposableEntity entity = gate.project(SCHEMA, VALUE_ONLY, pollutedPayload(), OBSERVED_AT);

        JsonObject nativeDoc = nativeProjector.project(entity);
        JsonObject ngsiDoc = ngsiLdProjector.project(entity);

        // Native holds value/unit flat; NGSI-LD wraps them (value -> numericValue on collision).
        assertEquals(14.7, nativeDoc.getDouble("value"));
        assertEquals(14.7, ngsiDoc.getJsonObject("numericValue").getDouble("value"));
        assertEquals("ug/m3", nativeDoc.getString("unit"));
        assertEquals("ug/m3", ngsiDoc.getJsonObject("unit").getString("value"));

        // metric is absent in both formats — the view narrowed the entity, not the projector.
        assertNull(nativeDoc.getValue("metric"));
        assertNull(ngsiDoc.getValue("metric"));
    }

    @Test
    void leakGuaranteeHoldsUnderView() {
        ExposableEntity entity = gate.project(SCHEMA, VALUE_ONLY, pollutedPayload(), OBSERVED_AT);

        for (String serialized : new String[]{
                nativeProjector.project(entity).encode(), ngsiLdProjector.project(entity).encode()}) {
            assertFalse(serialized.contains("deviceDbId"), serialized);
            assertFalse(serialized.contains("userId"), serialized);
            assertFalse(serialized.contains("partitionKey"), serialized);
        }
        assertTrue(true);
    }
}
