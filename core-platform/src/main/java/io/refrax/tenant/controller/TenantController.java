package io.refrax.tenant.controller;

import io.refrax.tenant.TenantAdminAllowlist;
import io.refrax.tenant.TenantContext;
import io.refrax.tenant.TenantProvisioningService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/v1/tenants")
public class TenantController {

    private static final Logger LOG = Logger.getLogger(TenantController.class);
    @Inject
    TenantProvisioningService provisioningService;

    @Inject
    TenantContext tenantContext;

    @Inject
    TenantAdminAllowlist adminAllowlist;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createTenant(@Valid CreateTenantRequest req) {
        if (!adminAllowlist.isAdmin(tenantContext.getTenantId())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Not allowed to provision tenants.")
                    .build();
        }

        try {
            provisioningService.provisionTenant(req.schema());
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            LOG.error("Error occurred while creating tenant", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An unexpected error occurred.")
                    .build();
        }
    }
}
