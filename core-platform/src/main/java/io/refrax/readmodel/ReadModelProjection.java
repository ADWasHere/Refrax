package io.refrax.readmodel;

import io.refrax.gate.ExposableEntity;
import io.vertx.core.json.JsonObject;

/**
 * Turns a gated {@link ExposableEntity} into the value map a read model materialises.
 *
 * <p>The GDPR seam: a field flagged {@code personalData} is never written to the read model
 * in cleartext. For today's sensor data nothing is personal, so this is a no-op, but the
 * consumer is built so it <em>could not</em> materialise a personal field even if one were
 * declared
 */
public final class ReadModelProjection {

    private ReadModelProjection() {
    }

    public static JsonObject exposedValues(final ExposableEntity entity) {
        JsonObject out = new JsonObject();
        entity.properties().forEach((name, property) -> {
            if (!property.personalData()) {
                out.put(name, property.value());
            }
        });
        return out;
    }
}
