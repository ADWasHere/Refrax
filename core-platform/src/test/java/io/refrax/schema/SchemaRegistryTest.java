package io.refrax.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cross-schema loading rules: two declarations may not claim the same event type, since a
 * read could not tell which schema to apply. Pure unit test, no Quarkus.
 */
class SchemaRegistryTest {

    @Test
    void rejectsTwoSchemasWithTheSameEventType() {
        List<String> locations = List.of(
                "schemas/air-quality-reading.json",
                "schemas/air-quality-reading-duplicate.json");

        assertThrows(SchemaValidationException.class, () -> SchemaRegistry.loadAll(locations));
    }

    @Test
    void loadsDistinctSchemas() {
        assertDoesNotThrow(() -> SchemaRegistry.loadAll(List.of("schemas/air-quality-reading.json")));
    }
}
