package io.refrax.tenant;

import java.util.List;
import java.util.Map;

/**
 * Utility to resolve a dot-separated claim path from a map of attributes/claims.
 */
public final class ClaimPathResolver {
    private ClaimPathResolver() {}

    public static String resolveFromAttributes(Map<String, Object> attributes, String claimPath) {
        if (attributes == null || claimPath == null || claimPath.isBlank()) return null;
        String[] parts = claimPath.split("\\.");
        Object current = attributes;
        for (String p : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                current = null;
                break;
            }
            current = map.get(p);
            if (current == null) break;
        }

        if (current == null) {
            // fallback: direct top-level attribute
            Object v = attributes.get(claimPath);
            current = v;
        }

        if (current instanceof String s) return s;
        if (current instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof String) return (String) l.get(0);
        return null;
    }
}
