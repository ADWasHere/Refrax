package io.refrax.tenant;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class TenantResolverFilter implements ContainerRequestFilter {

    @Inject
    TenantResolver tenantResolver;

    @Inject
    TenantContext tenantContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String tenantId = tenantResolver.resolveTenantId(requestContext);

        if (tenantId == null || tenantId.isBlank()) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("Tenant context missing or unauthenticated")
                    .build());
            return;
        }

        tenantContext.setTenantId(tenantId);
    }
}
