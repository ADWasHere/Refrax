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
        for (String location : locations) {
            EventSchema schema = SchemaLoader.load(location.trim());
            SchemaValidator.validate(schema);
            schemas.put(schema.eventType(), schema);
        }
    }

    public Optional<EventSchema> find(String eventType) {
        Objects.requireNonNull(eventType);

        if(eventType.isEmpty()){
            throw new IllegalArgumentException("EventType cannot be empty");
        }

        return Optional.ofNullable(schemas.get(eventType));
    }
}
