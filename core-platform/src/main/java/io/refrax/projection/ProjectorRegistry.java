package io.refrax.projection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes the available {@link Projector}s by their {@code format()}. The read layer
 * selects a projector by name without knowing the concrete implementations, so adding a
 * projector is a matter of contributing a bean — no change here or at the call site.
 */
@ApplicationScoped
public class ProjectorRegistry {

    private final Map<String, Projector> byFormat = new HashMap<>();

    @Inject
    public ProjectorRegistry(Instance<Projector> projectors) {
        for (Projector projector : projectors) {
            byFormat.put(projector.format(), projector);
        }
    }

    public Optional<Projector> byFormat(String format) {
        return Optional.ofNullable(byFormat.get(format));
    }
}
