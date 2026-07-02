package io.refrax.gate;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * The capability gate: the one place where exposability is decided. It constructs
 * an {@link ExposableEntity} from a schema and a raw payload by copying <b>only</b>
 * declared exposable fields. Everything else — every undeclared field — is denied by
 * default simply by never being read.
 *
 * <p>The entity URN is built solely from declared identity components.
 */
@ApplicationScoped
public class Gate {

    public ExposableEntity project(EventSchema schema, JsonObject payload, OffsetDateTime observedAt) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(observedAt);

        String urn = mintUrn(schema, payload);

        // Deny by default: iterate only declared exposable fields, never the payload keys
        Map<String, ExposableProperty> properties = new LinkedHashMap<>();
        for (FieldDeclaration field : schema.exposableFields()) {
            if (field.role() == FieldRole.IDENTITY) {
                continue;
            }
            if (payload.containsKey(field.name())) {
                properties.put(field.name(), new ExposableProperty(
                        payload.getValue(field.name()),
                        field.role(),
                        field.vocabularyUri(),
                        field.personalData()));
            }
        }

        return new ExposableEntity(urn, schema.eventType(), properties, observedAt);
    }

    private String mintUrn(EventSchema schema, JsonObject payload) {
        List<String> parts = new ArrayList<>();
        parts.add(schema.urnNamespace());
        parts.add(schema.eventType());
        for (FieldDeclaration id : schema.identityComponents()) {
            Object value = payload.getValue(id.name());
            if (value == null || String.valueOf(value).isBlank()) {
                throw new IllegalStateException(
                        "Cannot build URN: identity component '" + id.name()
                                + "' is missing or blank in the payload of event '" + schema.eventType() + "'");
            }
            parts.add(String.valueOf(value));
        }
        return String.join(":", parts);
    }
}
