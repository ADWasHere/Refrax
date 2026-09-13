package io.refrax.tenant;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class TenantResolverFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(TenantResolverFilter.class);

    @Inject
    Instance<TenantResolver> tenantResolver;

    @Inject
    TenantContext tenantContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String tenantId = tenantResolver.get().resolveTenantId(requestContext);

        if (tenantId == null || tenantId.isBlank()) {
            String path = requestContext.getUriInfo() != null ? requestContext.getUriInfo().getPath() : "?";
            LOG.warnf("Rejected request with no resolvable tenant identity: %s %s", requestContext.getMethod(), path);
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("Tenant context missing or unauthenticated")
                    .build());
            return;
        }

        tenantContext.setTenantId(tenantId);
    }
}
