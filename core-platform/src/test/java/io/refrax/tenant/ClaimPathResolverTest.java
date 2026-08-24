package io.refrax.tenant;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClaimPathResolverTest {

    @Test
    void resolvesTopLevelStringClaim() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("org_id", "real-org");

        String resolved = ClaimPathResolver.resolveFromAttributes(attrs, "org_id");
        assertEquals("real-org", resolved);
    }

    @Test
    void resolvesNestedClaim() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("id", "nested-id");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("org", nested);

        String resolved = ClaimPathResolver.resolveFromAttributes(attrs, "org.id");
        assertEquals("nested-id", resolved);
    }

    @Test
    void resolvesListClaimByTakingFirstEntry() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("groups", List.of("g1", "g2"));

        String resolved = ClaimPathResolver.resolveFromAttributes(attrs, "groups");
        assertEquals("g1", resolved);
    }

    @Test
    void returnsNullIfMissing() {
        Map<String, Object> attrs = new HashMap<>();
        String resolved = ClaimPathResolver.resolveFromAttributes(attrs, "does.not.exist");
        assertNull(resolved);
    }
}
