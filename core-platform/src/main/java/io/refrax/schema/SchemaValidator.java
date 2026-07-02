package io.refrax.schema;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, load-time validation of a declared schema. Covers only the structural
 * rules that construction cannot already guarantee:
 *
 * <ul>
 *   <li>the event type and URN namespace are present;</li>
 *   <li>no two declarations share a name (a field cannot hold conflicting roles);</li>
 *   <li>at least one identity component exists — an entity cannot be projected without
 *       an identity.</li>
 * </ul>
 *
 * <p>A field without a vocabulary binding, or an internal field, are not checked here
 * because they cannot be constructed in the first place. The first violation throws
 * {@link SchemaValidationException}.
 */
public final class SchemaValidator {

    private SchemaValidator() {
    }

    public static void validate(EventSchema schema) {
        Objects.requireNonNull(schema);

        if (isBlank(schema.eventType())) {
            throw new SchemaValidationException("Schema has no eventType");
        }
        if (isBlank(schema.urnNamespace())) {
            throw new SchemaValidationException(
                    "Schema '" + schema.eventType() + "' has no urnNamespace");
        }

        Set<String> seen = new HashSet<>();
        for (FieldDeclaration f : schema.fields()) {
            if (!seen.add(f.name())) {
                throw new SchemaValidationException(
                        "Schema '" + schema.eventType() + "' declares field '" + f.name()
                                + "' more than once (conflicting roles)");
            }
        }

        if (schema.identityComponents().isEmpty()) {
            throw new SchemaValidationException(
                    "Schema '" + schema.eventType() + "' declares no identity component; "
                            + "an entity cannot be projected without an identity");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
