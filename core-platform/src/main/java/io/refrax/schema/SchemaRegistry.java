package io.refrax.schema;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

/**
 * Holds the declared schemas, keyed by event type. The JSON declarations named by
 * {@code refrax.schema.locations} are loaded and validated eagerly at startup:
 * a missing or invalid declaration fails application boot rather than
 * surfacing at request time.
 */
@ApplicationScoped
public class SchemaRegistry {

    @ConfigProperty(name = "refrax.schema.locations")
    List<String> locations;

    private final Map<String, EventSchema> schemas = new HashMap<>();

    void onStart(@Observes StartupEvent event) {
        schemas.putAll(loadAll(locations));
    }

    /**
     * Loads, validates and indexes every declaration by event type. Two declarations
     * sharing an event type is a fatal ambiguity — a read would not know which schema to
     * apply — so it is rejected here rather than silently letting one overwrite the other.
     */
    static Map<String, EventSchema> loadAll(List<String> locations) {
        Map<String, EventSchema> byType = new HashMap<>();
        Map<String, String> declaredBy = new HashMap<>();
        for (String location : locations) {
            String source = location.trim();
            EventSchema schema = SchemaLoader.load(source);
            SchemaValidator.validate(schema);

            String previous = declaredBy.put(schema.eventType(), source);
            if (previous != null) {
                throw new SchemaValidationException(
                        "Duplicate schema for event type '" + schema.eventType()
                                + "' declared by both '" + previous + "' and '" + source + "'");
            }
            byType.put(schema.eventType(), schema);
        }
        return byType;
    }

    public Optional<EventSchema> find(String eventType) {
        Objects.requireNonNull(eventType);

        if(eventType.isEmpty()){
            throw new IllegalArgumentException("EventType cannot be empty");
        }

        return Optional.ofNullable(schemas.get(eventType));
    }
}
