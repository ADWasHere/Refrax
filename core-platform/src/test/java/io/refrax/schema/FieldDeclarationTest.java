package io.refrax.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A field declaration makes an illegal state unrepresentable: an exposable field with no
 * vocabulary binding cannot be constructed at all.
 */
class FieldDeclarationTest {

    @Test
    void cannotBuildAFieldWithoutVocabulary() {
        assertThrows(IllegalArgumentException.class,
                () -> new FieldDeclaration("value", FieldRole.PROPERTY, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldDeclaration("value", FieldRole.PROPERTY, "   ", false));
    }

    @Test
    void buildsAValidField() {
        assertDoesNotThrow(
                () -> FieldDeclaration.property("value", "https://qudt.org/schema/qudt/numericValue"));
    }
}
