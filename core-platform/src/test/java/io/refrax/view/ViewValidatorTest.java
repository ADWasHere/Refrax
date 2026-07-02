package io.refrax.view;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Load-time view validation against the schema: a view may only shrink the exposable set,
 * and may only be queried on fields it (or the identity/valid-time) makes available. Pure
 * unit test, no Quarkus.
 */
class ViewValidatorTest {

    private static final EventSchema SCHEMA = new EventSchema("AirQualityReading", "urn:refrax", List.of(
            FieldDeclaration.identity("sensorId", "https://schema.org/identifier"),
            FieldDeclaration.property("metric", "https://example.org/metric"),
            FieldDeclaration.property("value", "https://qudt.org/schema/qudt/numericValue"),
            FieldDeclaration.property("unit", "https://qudt.org/schema/qudt/unit")));

    private static QueryAxis exact(String field) {
        return new QueryAxis(field, MatchType.EXACT);
    }

    @Test
    void acceptsAValidView() {
        View view = new View("v", "AirQualityReading",
                List.of("value", "unit"), Map.of("sensor", exact("sensorId")));

        assertDoesNotThrow(() -> ViewValidator.validate(view, SCHEMA));
    }

    @Test
    void rejectsExposingAFieldTheSchemaDoesNotExpose() {
        View view = new View("v", "AirQualityReading",
                List.of("value", "deviceDbId"), Map.of("sensor", exact("sensorId")));

        assertThrows(ViewValidationException.class, () -> ViewValidator.validate(view, SCHEMA));
    }

    @Test
    void rejectsExposingTheIdentity() {
        View view = new View("v", "AirQualityReading",
                List.of("sensorId", "value"), Map.of("sensor", exact("sensorId")));

        assertThrows(ViewValidationException.class, () -> ViewValidator.validate(view, SCHEMA));
    }

    @Test
    void rejectsAnAxisOnAFieldTheViewDoesNotExpose() {
        // The view exposes only value, but declares an axis on metric — filtering on metric
        // would let a consumer probe a field the view hides.
        View view = new View("v", "AirQualityReading",
                List.of("value"), Map.of("byMetric", exact("metric")));

        assertThrows(ViewValidationException.class, () -> ViewValidator.validate(view, SCHEMA));
    }

    @Test
    void rejectsAnExactAxisOnAPersonalDataField() {
        EventSchema schema = new EventSchema("AirQualityReading", "urn:refrax", List.of(
                FieldDeclaration.identity("sensorId", "https://schema.org/identifier"),
                FieldDeclaration.property("owner", "https://schema.org/name").asPersonalData()));
        View view = new View("v", "AirQualityReading",
                List.of("owner"), Map.of("byOwner", exact("owner")));

        assertThrows(ViewValidationException.class, () -> ViewValidator.validate(view, schema));
    }
}
