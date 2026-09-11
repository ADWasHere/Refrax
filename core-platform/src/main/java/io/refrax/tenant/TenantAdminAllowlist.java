package io.refrax.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;

/**
 * Refrax does not do authentication; the gateway or token issuer in front of it does.
 * Provisioning a new tenant schema is a privileged operation though, unlike ordinary
 * tenant-scoped reads and writes, so it should not be open to whoever can set any
 * {@code X-Tenant-ID}. This is a minimal allowlist for that one endpoint, not a substitute
 * for real authentication or authorization in front of Refrax.
 */
@ApplicationScoped
public class TenantAdminAllowlist {

    @ConfigProperty(name = "refrax.tenant.admin-ids", defaultValue = "admin")
    String adminIds;

    public boolean isAdmin(String tenantId) {
        return isAdmin(adminIds, tenantId);
    }

    /** Package-visible so the matching logic can be unit-tested without a Quarkus context. */
    static boolean isAdmin(String adminIdsConfig, String tenantId) {
        if (tenantId == null) {
            return false;
        }
        return Arrays.stream(adminIdsConfig.split(","))
                .map(String::trim)
                .anyMatch(tenantId::equals);
    }
}
