package io.refrax.view;

import io.quarkus.runtime.StartupEvent;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaRegistry;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the declared views, keyed by name. The JSON declarations named by
 * {@code refrax.view.locations} are loaded and validated against their schemas eagerly at
 * startup (after {@link SchemaRegistry}): a missing schema, a too-wide view, or a duplicate
 * name fails application boot rather than surfacing at request time.
 */
@ApplicationScoped
public class ViewRegistry {

    @ConfigProperty(name = "refrax.view.locations")
    List<String> locations;

    @Inject
    SchemaRegistry schemas;

    private final Map<String, View> views = new HashMap<>();

    // Runs after SchemaRegistry (priority 100) so views can validate against loaded schemas.
    void onStart(@Observes @Priority(200) StartupEvent event) {
        Map<String, String> declaredBy = new HashMap<>();
        for (String location : locations) {
            String source = location.trim();
            View view = ViewLoader.load(source);

            EventSchema schema = schemas.find(view.eventType()).orElseThrow(() -> new ViewValidationException(
                    "View '" + view.name() + "' is over unknown event type '" + view.eventType() + "'"));
            ViewValidator.validate(view, schema);

            String previous = declaredBy.put(view.name(), source);
            if (previous != null) {
                throw new ViewValidationException(
                        "Duplicate view name '" + view.name()
                                + "' declared by both '" + previous + "' and '" + source + "'");
            }
            views.put(view.name(), view);
        }
    }

    public Optional<View> find(String name) {
        return Optional.ofNullable(views.get(name));
    }
}
