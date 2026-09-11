package io.refrax.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantAdminAllowlistTest {

    @Test
    void allowsExactMatch() {
        assertTrue(TenantAdminAllowlist.isAdmin("admin", "admin"));
    }

    @Test
    void allowsOneOfSeveralCommaSeparatedIds() {
        assertTrue(TenantAdminAllowlist.isAdmin("admin, ops, platform-team", "ops"));
    }

    @Test
    void rejectsUnlistedTenant() {
        assertFalse(TenantAdminAllowlist.isAdmin("admin", "some-random-tenant"));
    }

    @Test
    void rejectsNullTenant() {
        assertFalse(TenantAdminAllowlist.isAdmin("admin", null));
    }

    @Test
    void rejectsWhenConfigIsBlank() {
        assertFalse(TenantAdminAllowlist.isAdmin("", "admin"));
    }
}
