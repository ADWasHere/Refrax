package io.refrax.tenant;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class TenantContext {
    private String tenantId;

    void setTenantId(String tenantId) {
        if (this.tenantId != null) {
            throw new IllegalStateException("Tenant context is immutable once set!");
        }
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        if (tenantId == null) {
            throw new IllegalStateException("No tenant resolved for the current request.");
        }
        return tenantId;
    }
}
