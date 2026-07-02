package io.refrax.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural validation of a whole schema: the rules that construction of individual
 * fields cannot guarantee (identity presence, unique names, event-type/namespace).
 */
class SchemaValidatorTest {

    @Test
    void acceptsAValidSchema() {
        EventSchema schema = new EventSchema("Reading", "urn:refrax", List.of(
                FieldDeclaration.identity("sensorId", "https://schema.org/identifier"),
                FieldDeclaration.property("value", "https://qudt.org/schema/qudt/numericValue")));

        assertDoesNotThrow(() -> SchemaValidator.validate(schema));
    }

    @Test
    void rejectsSchemaWithoutIdentity() {
        EventSchema schema = new EventSchema("Reading", "urn:refrax", List.of(
                FieldDeclaration.property("value", "https://qudt.org/schema/qudt/numericValue")));

        assertThrows(SchemaValidationException.class, () -> SchemaValidator.validate(schema));
    }

    @Test
    void rejectsDuplicateFieldNames() {
        EventSchema schema = new EventSchema("Reading", "urn:refrax", List.of(
                FieldDeclaration.identity("sensorId", "https://schema.org/identifier"),
                FieldDeclaration.property("value", "https://qudt.org/schema/qudt/numericValue"),
                FieldDeclaration.property("value", "https://qudt.org/schema/qudt/numericValue")));

        assertThrows(SchemaValidationException.class, () -> SchemaValidator.validate(schema));
    }
}
