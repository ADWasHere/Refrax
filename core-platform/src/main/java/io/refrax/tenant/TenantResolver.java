package io.refrax.tenant;

import jakarta.ws.rs.container.ContainerRequestContext;

public interface TenantResolver {
    String resolveTenantId(ContainerRequestContext requestContext);
}