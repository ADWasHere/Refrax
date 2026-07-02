package io.refrax.gate;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;
import io.refrax.view.View;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * The capability gate: the one place where exposability is decided. It constructs
 * an {@link ExposableEntity} from a schema and a raw payload by copying <b>only</b>
 * declared exposable fields. Everything else — every undeclared field — is denied by
 * default simply by never being read.
 *
 * <p>A view narrows further: when one is given, only the fields it exposes are copied. The
 * view is applied here, before any projector, so every projector inherits the narrowed set
 * for free and the leak guarantee holds unchanged. The entity URN is always built solely
 * from declared identity components.
 */
@ApplicationScoped
public class Gate {

    /** Full projection: every exposable (non-identity) field the schema declares. */
    public ExposableEntity project(EventSchema schema, JsonObject payload, OffsetDateTime observedAt) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(observedAt);
        return build(schema, payload, observedAt, field -> true);
    }

    /** View projection: only the fields the view exposes (a subset of the schema's). */
    public ExposableEntity project(EventSchema schema, View view, JsonObject payload, OffsetDateTime observedAt) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(view);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(observedAt);
        return build(schema, payload, observedAt, field -> view.exposesField(field.name()));
    }

    private ExposableEntity build(EventSchema schema, JsonObject payload, OffsetDateTime observedAt,
                                  Predicate<FieldDeclaration> included) {
        String urn = mintUrn(schema, payload);

        // Deny by default: iterate only declared exposable fields, never the payload keys.
        // Identity is carried by the URN, not repeated as a property.
        Map<String, ExposableProperty> properties = new LinkedHashMap<>();
        for (FieldDeclaration field : schema.exposableFields()) {
            if (field.role() == FieldRole.IDENTITY || !included.test(field)) {
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
