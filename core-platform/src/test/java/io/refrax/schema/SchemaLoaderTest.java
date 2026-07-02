package io.refrax.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The declaration is a JSON document loaded from the classpath. Verifies the loader maps
 * roles, vocabulary bindings and declaration order (which drives identity/URN ordering)
 * correctly. Pure unit test, no Quarkus.
 */
class SchemaLoaderTest {

    @Test
    void loadsAirQualityReadingFromJson() {
        EventSchema schema = SchemaLoader.load("schemas/air-quality-reading.json");

        assertEquals("AirQualityReading", schema.eventType());
        assertEquals("urn:refrax", schema.urnNamespace());

        // Roles are parsed case-insensitively from the JSON's "exposable" section.
        assertEquals(FieldRole.IDENTITY, schema.field("sensorId").orElseThrow().role());
        assertEquals(FieldRole.PROPERTY, schema.field("value").orElseThrow().role());

        // Vocabulary binding survives the round-trip.
        assertEquals("https://schema.org/identifier",
                schema.field("sensorId").orElseThrow().vocabularyUri());

        // sensorId is the sole identity component; all four fields are exposable.
        List<FieldDeclaration> identity = schema.identityComponents();
        assertEquals(1, identity.size());
        assertEquals("sensorId", identity.get(0).name());
        assertEquals(4, schema.exposableFields().size());

        // The declared schema passes validation.
        SchemaValidator.validate(schema);
    }
}
