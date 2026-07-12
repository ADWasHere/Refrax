package io.refrax.readmodel;

import io.refrax.gate.ExposableEntity;
import io.refrax.gate.ExposableProperty;
import io.refrax.schema.FieldRole;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GDPR seam: a read model never materialises a {@code personalData} field in cleartext.
 * The consumer is built so it could not, even before P7 exists.
 */
class ReadModelProjectionTest {

    @Test
    void personalDataFieldsAreNotMaterialised() {
        Map<String, ExposableProperty> properties = new LinkedHashMap<>();
        properties.put("value", new ExposableProperty(12.3, FieldRole.PROPERTY, "https://qudt.org/x", false));
        properties.put("owner", new ExposableProperty("alice", FieldRole.PROPERTY, "https://schema.org/name", true));
        ExposableEntity entity = new ExposableEntity("urn:refrax:X:1", "X", properties, null);

        JsonObject exposed = ReadModelProjection.exposedValues(entity);

        assertEquals(12.3, exposed.getDouble("value"));
        assertTrue(exposed.containsKey("value"));
        assertFalse(exposed.containsKey("owner"), "personal field must not be materialised");
    }
}
