package io.refrax.view;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A named view over one event type. It exposes a subset of the schema's exposable
 * properties/relationships and declares the axes it may be queried on. A view can only
 * shrink what the schema marks exposable, never widen it, which is how data minimisation
 * becomes structural. Identity is never listed in {@code exposes}: it is structural and
 * always goes into the URN, whichever view renders the entity.
 *
 * @param name      the view name (used in the read path)
 * @param eventType the event type this view is over
 * @param exposes   the exposed property/relationship field names (a subset of schema-exposable)
 * @param queryable the declared query axes, keyed by axis name
 */
public record View(
        String name,
        String eventType,
        List<String> exposes,
        Map<String, QueryAxis> queryable) {

    public View {
        exposes = List.copyOf(exposes);
        queryable = Map.copyOf(queryable);
    }

    public boolean exposesField(String fieldName) {
        return exposes.contains(fieldName);
    }

    public Optional<QueryAxis> axis(String axisName) {
        return Optional.ofNullable(queryable.get(axisName));
    }
}
