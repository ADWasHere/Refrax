package io.refrax.projection;

import io.refrax.gate.ExposableEntity;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The SPI proof: the leak guarantee holds for <em>any</em> projector, generically, without
 * knowing which one. Every projector is handed the same already-gated entity and its output
 * is checked for internal strings. Because the guarantee sits on the boundary (the entity
 * never carries internal fields), it extends to projectors not written yet — a new one only
 * has to implement {@link Projector} to inherit it.
 */
class ProjectorSpiTest {

    private final List<Projector> projectors = List.of(new NativeProjector(), new NgsiLdProjector());

    @Test
    void noProjectorLeaksInternalFields() {
        ExposableEntity entity = ProjectionFixtures.pollutedReading("sensor-42");

        for (Projector projector : projectors) {
            JsonObject doc = projector.project(entity);
            String serialized = doc.encode();

            assertFalse(serialized.contains("deviceDbId"), projector.format() + ": " + serialized);
            assertFalse(serialized.contains("userId"), projector.format() + ": " + serialized);
            assertFalse(serialized.contains("partitionKey"), projector.format() + ": " + serialized);
        }
    }
}
