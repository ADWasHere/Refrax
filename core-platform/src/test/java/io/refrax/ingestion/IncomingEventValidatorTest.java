package io.refrax.ingestion;

import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaLoader;
import io.refrax.schema.SchemaRegistry;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomingEventValidatorTest {

    private static SchemaRegistry registryForAirQuality() {
        EventSchema schema = SchemaLoader.load("schemas/air-quality-reading.json");

        return new SchemaRegistry() {
            @Override
            public Optional<EventSchema> find(String eventType) {
                return eventType.equals("AirQualityReading") ? Optional.of(schema) : Optional.empty();
            }
        };
    }

    @Test
    void validEventIsMappedToIncomingEvent() {
        IncomingEventValidator validator = new IncomingEventValidator(registryForAirQuality());

        JsonObject event = new JsonObject()
                .put("eventType", "AirQualityReading")
                .put("observedAt", "2026-08-22T10:00:00Z")
                .put("payload", new JsonObject()
                        .put("sensorId", "sensor-7")
                        .put("metric", "pm25")
                        .put("value", 42)
                        .put("unit", "µg/m³"));

        IncomingEvent incoming = assertDoesNotThrow(() -> validator.from(event));

        assertEquals("AirQualityReading", incoming.eventType());
        assertEquals(42, ((Number) incoming.fields().get("value")).intValue());
        assertEquals("sensor-7", incoming.fields().get("sensorId"));
    }

    @Test
    void validStringValuesAreParsedToTypedPayloadValues() {
        IncomingEventValidator validator = new IncomingEventValidator(registryForAirQuality());

        JsonObject event = new JsonObject()
                .put("eventType", "AirQualityReading")
                .put("observedAt", "2026-08-22T10:00:00Z")
                .put("payload", new JsonObject()
                        .put("sensorId", "sensor-7")
                        .put("metric", "pm25")
                        .put("value", "42.5")
                        .put("unit", "µg/m³"));

        IncomingEvent incoming = assertDoesNotThrow(() -> validator.from(event));
        Object value = incoming.fields().get("value");

        assertInstanceOf(BigDecimal.class, value);
        assertEquals(new BigDecimal("42.5"), value);
    }

    @Test
    void booleanStringIsParsedAndUnknownFieldsAreIgnored() {
        IncomingEventValidator validator = new IncomingEventValidator(registryForAirQuality());

        JsonObject event = new JsonObject()
                .put("eventType", "AirQualityReading")
                .put("observedAt", "2026-08-22T10:00:00Z")
                .put("payload", new JsonObject()
                        .put("sensorId", "sensor-7")
                        .put("metric", "pm25")
                        .put("value", "8")
                        .put("unit", "µg/m³")
                        .put("debugFlag", "true")
                        .put("unused", "ignored"));

        IncomingEvent incoming = assertDoesNotThrow(() -> validator.from(event));

        assertEquals(new BigDecimal("8"), incoming.fields().get("value"));
        assertTrue(incoming.fields().containsKey("sensorId"));
        assertTrue(!incoming.fields().containsKey("debugFlag"));
        assertTrue(!incoming.fields().containsKey("unused"));
    }

    @Test
    void booleanStringValuesAreParsedForBooleanFields() {
        EventSchema schema = new EventSchema("ToggleEvent", "urn:refrax", java.util.List.of(
                new io.refrax.schema.FieldDeclaration("active", io.refrax.schema.FieldRole.PROPERTY,
                        io.refrax.schema.FieldType.BOOLEAN, "https://example.com/active", false)));
        SchemaRegistry registry = new SchemaRegistry() {
            @Override
            public Optional<EventSchema> find(String eventType) {
                return eventType.equals("ToggleEvent") ? Optional.of(schema) : Optional.empty();
            }
        };

        IncomingEventValidator validator = new IncomingEventValidator(registry);
        JsonObject event = new JsonObject()
                .put("eventType", "ToggleEvent")
                .put("observedAt", "2026-08-22T10:00:00Z")
                .put("payload", new JsonObject().put("active", "true"));

        IncomingEvent incoming = assertDoesNotThrow(() -> validator.from(event));
        assertEquals(Boolean.TRUE, incoming.fields().get("active"));
    }

    @Test
    void invalidNumberStringIsRejected() {
        IncomingEventValidator validator = new IncomingEventValidator(registryForAirQuality());

        JsonObject event = new JsonObject()
                .put("eventType", "AirQualityReading")
                .put("observedAt", "2026-08-22T10:00:00Z")
                .put("payload", new JsonObject()
                        .put("sensorId", "sensor-7")
                        .put("metric", "pm25")
                        .put("value", "kaputt")
                        .put("unit", "µg/m³"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.from(event));
        assertTrue(ex.getMessage().contains("not a valid number") || ex.getMessage().contains("expected type NUMBER"));
    }
}
