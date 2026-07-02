package io.refrax.projection;

import io.refrax.gate.ExposableEntity;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The consistency proof: two projections of the same entity carry the same facts, differing
 * only in shape. Same URN, same number of exposed facts, same set of values — even though
 * native exposes each fact under its neutral name and NGSI-LD may rename a colliding field.
 * This is the "one truth, many faces, consistent by construction" claim: because both
 * projections are rendered from the same {@link ExposableEntity}, they cannot drift.
 */
class ProjectionConsistencyTest {

    private final NativeProjector nativeProjector = new NativeProjector();
    private final NgsiLdProjector ngsiLdProjector = new NgsiLdProjector();

    @Test
    void nativeAndNgsiLdCarryTheSameFacts() {
        ExposableEntity entity = ProjectionFixtures.cleanReading("sensor-42");

        JsonObject nativeDoc = nativeProjector.project(entity);
        JsonObject ngsiDoc = ngsiLdProjector.project(entity);

        // Same identity.
        assertEquals(nativeDoc.getString("id"), ngsiDoc.getString("id"));

        // Same exposed facts once each projection's structural keys are removed: same count
        // and same set of values. Native holds each value flat; NGSI-LD wraps it in a
        // Property node (possibly under a renamed term), so we compare by value.
        Set<String> nativeKeys = new HashSet<>(nativeDoc.fieldNames());
        nativeKeys.removeAll(Set.of("id", "type", "observedAt"));
        Set<String> ngsiKeys = new HashSet<>(ngsiDoc.fieldNames());
        ngsiKeys.removeAll(Set.of("id", "type", "@context"));

        assertEquals(entity.properties().size(), nativeKeys.size());
        assertEquals(entity.properties().size(), ngsiKeys.size());

        Set<Object> nativeValues = nativeKeys.stream()
                .map(nativeDoc::getValue)
                .collect(Collectors.toSet());
        Set<Object> ngsiValues = ngsiKeys.stream()
                .map(k -> ngsiDoc.getJsonObject(k).getValue("value"))
                .collect(Collectors.toSet());
        assertEquals(nativeValues, ngsiValues);
    }
}
