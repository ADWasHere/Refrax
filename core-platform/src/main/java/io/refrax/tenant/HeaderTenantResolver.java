package io.refrax.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

@ApplicationScoped
public class HeaderTenantResolver implements TenantResolver {

    @Override
    public String resolveTenantId(ContainerRequestContext requestContext) {
        return requestContext.getHeaderString("X-Tenant-ID");
    }
}
