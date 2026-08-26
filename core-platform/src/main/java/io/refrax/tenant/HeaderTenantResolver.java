package io.refrax.tenant;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Reads tenant from a header. Only active when refrax.tenant.resolver=header at build time.
 * <br/>
 * <b>NOTE:<b/> This is only secure behind a trusted gateway that sets the header.
 */
@LookupIfProperty(name = "refrax.tenant.resolver", stringValue = "header")
@ApplicationScoped
public class HeaderTenantResolver implements TenantResolver {

    @Override
    public String resolveTenantId(ContainerRequestContext requestContext) {
        return requestContext.getHeaderString("X-Tenant-ID");
    }
}
