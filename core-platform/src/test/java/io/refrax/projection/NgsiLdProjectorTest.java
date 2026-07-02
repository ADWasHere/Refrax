package io.refrax.projection;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The NGSI-LD projector turns the same neutral entity into the standard-specific NGSI-LD
 * form: identity in {@code id}, each field as a typed {@code Property} node, and a
 * {@code @context} array of term→vocabulary mappings plus the core context. Fields whose
 * name collides with an NGSI-LD reserved key (here {@code value}) are exposed under a term
 * derived from their vocabulary URI. It also upholds the leak guarantee without doing
 * anything itself against leaks — it only receives the already-gated entity.
 */
class NgsiLdProjectorTest {

    private static final String CORE_CONTEXT =
            "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld";

    private final NgsiLdProjector projector = new NgsiLdProjector();

    @Test
    void rendersNgsiLdShapeWithDerivedContext() {
        JsonObject doc = projector.project(ProjectionFixtures.cleanReading("sensor-42"));

        assertEquals("urn:refrax:AirQualityReading:sensor-42", doc.getString("id"));
        assertEquals("AirQualityReading", doc.getString("type"));

        // "value" collides with an NGSI-LD reserved key, so it is exposed under a term
        // derived from its vocabulary URI (.../qudt/numericValue -> numericValue).
        JsonObject numericValue = doc.getJsonObject("numericValue");
        assertEquals("Property", numericValue.getString("type"));
        assertEquals(12.3, numericValue.getDouble("value"));

        // Non-colliding names are kept as-is.
        JsonObject metric = doc.getJsonObject("metric");
        assertEquals("Property", metric.getString("type"));
        assertEquals("PM2.5", metric.getString("value"));

        // @context is an array: derived term→vocabulary mappings first, then the core context.
        JsonArray context = doc.getJsonArray("@context");
        JsonObject terms = context.getJsonObject(0);
        assertEquals("https://qudt.org/schema/qudt/numericValue", terms.getString("numericValue"));
        assertEquals("https://qudt.org/schema/qudt/unit", terms.getString("unit"));
        assertEquals("https://smartdatamodels.org/dataModel.Environment/typeOfMeasurement",
                terms.getString("metric"));
        assertEquals(CORE_CONTEXT, context.getString(1));
    }

    @Test
    void doesNotLeakInternalFields() {
        JsonObject doc = projector.project(ProjectionFixtures.pollutedReading("sensor-42"));

        String serialized = doc.encode();
        assertFalse(serialized.contains("deviceDbId"), serialized);
        assertFalse(serialized.contains("userId"), serialized);
        assertFalse(serialized.contains("partitionKey"), serialized);
    }
}
