package io.refrax.view;

import io.quarkus.runtime.StartupEvent;
import io.refrax.schema.EventSchema;
import io.refrax.schema.SchemaRegistry;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The single place downstream gets a {@link ViewBinding}: a view paired with its schema and
 * validated to fit. Bindings are built once at startup (after the view and schema catalogs),
 * so an unknown schema or a too-wide view fails boot — not a request — and no reader ever
 * re-validates the pairing.
 */
@ApplicationScoped
public class ViewBindingRegistry {

    @Inject
    ViewRegistry views;

    @Inject
    SchemaRegistry schemas;

    private final Map<String, ViewBinding> bindings = new HashMap<>();

    // Runs after ViewRegistry (200) and SchemaRegistry (100).
    void onStart(@Observes @Priority(300) StartupEvent event) {
        for (View view : views.all()) {
            EventSchema schema = schemas.find(view.eventType()).orElseThrow(() ->
                    new ViewValidationException("View '" + view.name() + "' is over unknown event type '" + view.eventType() + "'"));

            ViewBinding binding = new ViewBinding(view, schema);
            if (bindings.put(view.name(), binding) != null) {
                throw new ViewValidationException(
                        "Duplicate binding for view " + view.name() + ": " + binding
                );
            }
        }
    }

    public Optional<ViewBinding> find(String name) {
        return Optional.ofNullable(bindings.get(name));
    }
}
