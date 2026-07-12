package io.refrax.view;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A catalog of the declared views, keyed by name. It only loads and holds views — it does not
 * know about schemas. Binding a view to its schema (and validating the two fit) is the job of
 * {@link ViewBindingRegistry}. The JSON declarations named by {@code refrax.view.locations}
 * are loaded at startup; a duplicate view name fails boot.
 */
@ApplicationScoped
public class ViewRegistry {

    @ConfigProperty(name = "refrax.view.locations")
    List<String> locations;

    private final Map<String, View> views = new HashMap<>();

    // Runs after SchemaRegistry (priority 100) and before ViewBindingRegistry (priority 300).
    void onStart(@Observes @Priority(200) StartupEvent event) {
        Map<String, String> declaredBy = new HashMap<>();
        for (String location : locations) {
            String source = location.trim();
            View view = ViewLoader.load(source);

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

    public Collection<View> all() {
        return views.values();
    }
}
