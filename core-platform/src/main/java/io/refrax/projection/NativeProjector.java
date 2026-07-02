package io.refrax.projection;

import io.refrax.gate.ExposableEntity;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Renders an entity in Refrax's own flat shape: the id, the type, the valid-time, and each
 * exposable property as a plain value. Identity is present only in the id, never repeated
 * as a field.
 */
@ApplicationScoped
public class NativeProjector implements Projector {

    @Override
    public String format() {
        return "native";
    }

    @Override
    public JsonObject project(ExposableEntity entity) {
        JsonObject out = new JsonObject();
        out.put("id", entity.id());
        out.put("type", entity.type());
        if (entity.observedAt() != null) {
            out.put("observedAt", entity.observedAt().toString());
        }
        entity.properties().forEach((name, prop) -> out.put(name, prop.value()));
        return out;
    }
}
