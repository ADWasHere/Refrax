package io.refrax.tenant;

import io.refrax.tenant.exceptions.InvalidTenantIdException;
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
    void acceptsHyphenatedIdentifiers() {
        // Regression: a real production incident. The schema is always interpolated into a
        // double-quoted identifier, so a hyphen is safe there (unlike in a bare/unquoted
        // identifier) — but was rejected anyway, taking down every tenant whose id happened to
        // contain one (a common shape: kebab-case org slugs, UUID-derived ids).
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("does-not-exist"));
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("acme-corp-1"));
    }

    @Test
    void rejectsNull() {
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema(""));
    }

    @Test
    void rejectsIdentifiersStartingWithADigit() {
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema("1tenant"));
    }

    @Test
    void rejectsQuoteInjectionAttempts() {
        assertThrows(InvalidTenantIdException.class,
                () -> TenantAwarePanache.requireSafeSchema("public\"; DROP TABLE events; --"));
    }

    @Test
    void rejectsWhitespaceAndSpecialCharacters() {
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema("tenant one"));
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema("tenant;drop"));
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema("tenant.other"));
    }

    @Test
    void rejectsIdentifiersLongerThan63Characters() {
        String tooLong = "a".repeat(64);
        assertThrows(InvalidTenantIdException.class, () -> TenantAwarePanache.requireSafeSchema(tooLong));
        assertDoesNotThrow(() -> TenantAwarePanache.requireSafeSchema("a".repeat(63)));
    }
}
