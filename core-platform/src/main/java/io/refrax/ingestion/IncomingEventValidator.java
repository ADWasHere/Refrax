package io.refrax.ingestion;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldType;
import io.refrax.schema.SchemaRegistry;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts a raw ingress payload into {@link IncomingEvent} and validates it against the
 * corresponding runtime schema
 */
@ApplicationScoped
public class IncomingEventValidator {

    @Inject
    SchemaRegistry schemaRegistry;

    public IncomingEventValidator() {
    }

    public IncomingEventValidator(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * Parses a JSON event envelope into a validated domain object.
     *
     * @param event the incoming JSON envelope; must contain at least an eventType and payload
     * @return a validated incoming-event record
     * @throws IllegalArgumentException for unknown event types or schema/type mismatches
     */
    public IncomingEvent from(JsonObject event) {
        Objects.requireNonNull(event, "event");

        String eventType = event.getString("eventType");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Incoming event is missing required field 'eventType'");
        }

        EventSchema schema = schemaRegistry.find(eventType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown eventType: " + eventType));

        JsonObject payload = event.getJsonObject("payload");
        if (payload == null) {
            throw new IllegalArgumentException("Incoming event for type '" + eventType + "' is missing payload");
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.getMap().entrySet()) {
            String fieldName = entry.getKey();
            Optional<FieldDeclaration> declaration = schema.field(fieldName);
            if (declaration.isEmpty()) {
                continue; // Ignore unknown fields; they are not part of the schema but won't block the event from being ingested
            }

            Object value = normalizeValue(entry.getValue(), declaration.get().type());
            validateValueType(eventType, fieldName, value, declaration.get().type());
            fields.put(fieldName, value);
        }

        Instant occurredAt = parseOccurredAt(event.getString("observedAt"));
        return new IncomingEvent(eventType, Map.copyOf(fields), occurredAt);
    }

    private static Instant parseOccurredAt(String observedAt) {
        if (observedAt == null || observedAt.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(observedAt).toInstant();
        } catch (Exception e) {
            throw new IllegalArgumentException("Field 'observedAt' is not a valid ISO-8601 timestamp: " + observedAt, e);
        }
    }

    private static void validateValueType(String eventType, String fieldName, Object value, FieldType expectedType) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' in eventType '" + eventType + "' is null but schema expects " + expectedType);
        }

        boolean valid = switch (expectedType) {
            case STRING -> value instanceof String;

            case NUMBER -> value instanceof Number
                    || (value instanceof String s && isValidNumber(s));

            case BOOLEAN -> value instanceof Boolean
                    || (value instanceof String s && isValidBoolean(s));

            case TIMESTAMP -> value instanceof Instant
                    || value instanceof OffsetDateTime
                    || (value instanceof String s && isValidTimestamp(s));
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' in eventType '" + eventType + "' expected type " + expectedType
                            + " but received " + value.getClass().getSimpleName() + " with value " + value);
        }
    }

    private static Object normalizeValue(Object value, FieldType expectedType) {
        if (value == null) {
            return null;
        }

        return switch (expectedType) {
            case NUMBER -> value instanceof Number ? value : parseNumber((String) value);
            case BOOLEAN -> value instanceof Boolean ? value : parseBoolean((String) value);
            case TIMESTAMP -> value instanceof String s && isValidTimestamp(s) ? OffsetDateTime.parse(s) : value;
            case STRING -> value;
        };
    }

    private static boolean isValidTimestamp(String candidate) {
        try {
            OffsetDateTime.parse(candidate);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object parseNumber(String candidate) {
        try {
            return new java.math.BigDecimal(candidate);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field value '" + candidate + "' is not a valid number", e);
        }
    }

    private static Boolean parseBoolean(String candidate) {
        if (!isValidBoolean(candidate)) {
            throw new IllegalArgumentException("Field value '" + candidate + "' is not a valid boolean");
        }
        return Boolean.parseBoolean(candidate);
    }

    private static boolean isValidNumber(String candidate) {
        try {
            Double.parseDouble(candidate);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidBoolean(String candidate) {
        return "true".equalsIgnoreCase(candidate) || "false".equalsIgnoreCase(candidate);
    }
}
