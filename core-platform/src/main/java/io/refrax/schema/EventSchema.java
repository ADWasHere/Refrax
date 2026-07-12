package io.refrax.schema;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The declared schema of one event type: the ordered set of exposable
 * {@link FieldDeclaration}s plus the URN namespace used to mint entity ids from identity
 * components. This is the standard-agnostic core that projectors later convert
 * into NGSI-LD, OGC SensorThings, etc.
 *
 * @param eventType    the event type this schema describes (e.g. {@code AirQualityReading})
 * @param urnNamespace the URN namespace prefix (e.g. {@code urn:refrax})
 * @param fields       the declared (exposable) field declarations, in declaration order
 */
public record EventSchema(
        String eventType,
        String urnNamespace,
        List<FieldDeclaration> fields) {

    public EventSchema {
        fields = List.copyOf(fields);
    }

    /** Identity components, in declaration order; the URN is built solely from these. */
    public List<FieldDeclaration> identityComponents() {
        return fields.stream().filter(f -> f.role() == FieldRole.IDENTITY).toList();
    }

    /** All exposable fields, in declaration order. */
    public List<FieldDeclaration> exposableFields() {
        return fields.stream().filter(FieldDeclaration::exposable).toList();
    }

    /**
     * The entity URN for the given identity component values (in declaration order). The one
     * place a URN is assembled, so the gate that writes {@code entity_id} and any reader that
     * filters on it cannot drift apart.
     */
    public String urn(List<String> identityValues) {
        return identityValues.isEmpty()
                ? urnNamespace + ":" + eventType
                : urnNamespace + ":" + eventType + ":" + String.join(":", identityValues);
    }

    public Optional<FieldDeclaration> field(String name) {
        Objects.requireNonNull(name);

        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }
}
