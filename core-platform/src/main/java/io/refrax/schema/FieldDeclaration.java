package io.refrax.schema;

import java.util.Objects;

/**
 * Declaration of a single exposable field: its semantic {@link FieldRole}, the mandatory
 * vocabulary URI that binds it to a shared meaning, and whether it carries personal data.
 *
 * <p>Only exposable fields are declared, so a declaration always names something that can
 * be exposed. The vocabulary binding is required and enforced on construction: a field
 * without a declared meaning cannot be built, not merely rejected later.
 *
 * @param name          the field name as it appears in the event payload
 * @param role          the semantic role (drives exposability and URN construction)
 * @param vocabularyUri the vocabulary binding; must be present and non-blank
 * @param personalData  whether the field is (or may be) personal data
 */
public record FieldDeclaration(
        String name,
        FieldRole role,
        String vocabularyUri,
        boolean personalData) {

    public FieldDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        if (vocabularyUri == null || vocabularyUri.isBlank()) {
            throw new IllegalArgumentException(
                    "Field '" + name + "' with role " + role + " requires a vocabulary binding");
        }
    }

    public boolean exposable() {
        return role.exposable();
    }

    // --- Convenience factories keeping test/call-site declarations readable ----------

    public static FieldDeclaration identity(String name, String vocabularyUri) {
        return new FieldDeclaration(name, FieldRole.IDENTITY, vocabularyUri, false);
    }

    public static FieldDeclaration property(String name, String vocabularyUri) {
        return new FieldDeclaration(name, FieldRole.PROPERTY, vocabularyUri, false);
    }

    public static FieldDeclaration relationship(String name, String vocabularyUri) {
        return new FieldDeclaration(name, FieldRole.RELATIONSHIP, vocabularyUri, false);
    }

    /** Returns a copy of this declaration flagged as personal data. */
    public FieldDeclaration asPersonalData() {
        return new FieldDeclaration(name, role, vocabularyUri, true);
    }
}
