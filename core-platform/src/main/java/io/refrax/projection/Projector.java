package io.refrax.projection;

import io.refrax.gate.ExposableEntity;
import io.vertx.core.json.JsonObject;

/**
 * The projection extension boundary. A projector renders an {@link ExposableEntity} into a
 * concrete standard shape (native, NGSI-LD, ...). It decides only <em>shape</em>, never
 * eligibility: it is handed an entity that already contains only exposable fields, so it
 * cannot emit an internal field — the guarantee lives on this boundary, not in each
 * projector. Any projector added here inherits it, including ones not yet written.
 */
public interface Projector {

    /** Stable identifier used to select this projector (e.g. {@code native}, {@code ngsi-ld}). */
    String format();

    /** Render the entity into this projector's standard shape. */
    JsonObject project(ExposableEntity entity);
}
