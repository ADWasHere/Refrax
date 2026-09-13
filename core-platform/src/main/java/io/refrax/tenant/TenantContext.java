package io.refrax.tenant;

import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.MDC;

@RequestScoped
public class TenantContext {
    private String tenantId;

    public void setTenantId(String tenantId) {
        if (this.tenantId != null) {
            throw new IllegalStateException("Tenant context is immutable once set!");
        }
        this.tenantId = tenantId;
        MDC.put("tenant.id", tenantId);
    }

    public String getTenantId() {
        if (tenantId == null) {
            throw new IllegalStateException("No tenant resolved for the current request.");
        }
        return tenantId;
    }

    public void cleanup(@Observes @Destroyed(RequestScoped.class) Object event) {
        MDC.remove("tenant.id");
    }
}
