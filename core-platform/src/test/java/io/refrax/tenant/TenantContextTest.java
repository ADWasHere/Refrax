package io.refrax.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @Test
    void tenantContextIsImmutableOnceSet() {
        TenantContext ctx = new TenantContext();

        // no tenant yet -> get should throw
        assertThrows(IllegalStateException.class, ctx::getTenantId);

        ctx.setTenantId("tenant-1");
        assertEquals("tenant-1", ctx.getTenantId());

        // second set attempt should fail
        assertThrows(IllegalStateException.class, () -> ctx.setTenantId("other"));
    }
}
