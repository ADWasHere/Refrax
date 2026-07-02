package io.refrax.gate;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * The gate's output: a standard-agnostic entity that, <b>by construction</b>, contains
 * only declared exposable fields. This is the guarantee boundary — the value handed to
 * any projector simply does not contain an internal field, so a projector cannot emit
 * one. Projectors decide only shape from this, never eligibility.
 *
 * @param id         the entity URN, built solely from declared identity components
 * @param type       the entity/event type
 * @param properties the exposable properties, keyed by field name
 * @param observedAt the valid-time of the underlying event
 */
public record ExposableEntity(
        String id,
        String type,
        Map<String, ExposableProperty> properties,
        OffsetDateTime observedAt) {

    public ExposableEntity {
        properties = Map.copyOf(properties);
    }
}
