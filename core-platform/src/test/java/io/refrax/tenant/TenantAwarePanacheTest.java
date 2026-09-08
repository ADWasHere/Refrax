package io.refrax.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The schema identifier is interpolated directly into a {@code SET LOCAL search_path} statement
 * (Postgres has no bind-parameter syntax for identifiers), so a bad tenant id must be rejected
 * before it ever reaches that SQL string.
 */
class TenantAwarePanacheTest {

    @Test
    void acceptsPlainIdentifiers() {
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("public"));
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("tenant_1"));
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("_tenant"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema(""));
    }

    @Test
    void rejectsIdentifiersStartingWithADigit() {
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema("1tenant"));
    }

    @Test
    void rejectsQuoteInjectionAttempts() {
        assertThrows(IllegalStateException.class,
                () -> TenantAwarePanache.requireSafeSchema("public\"; DROP TABLE events; --"));
    }

    @Test
    void rejectsWhitespaceAndSpecialCharacters() {
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema("tenant one"));
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema("tenant;drop"));
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema("tenant.other"));
    }

    @Test
    void rejectsIdentifiersLongerThan63Characters() {
        String tooLong = "a".repeat(64);
        assertThrows(IllegalStateException.class, () -> TenantAwarePanache.requireSafeSchema(tooLong));
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("a".repeat(63)));
    }
}
