package io.refrax.tenant.controller;

import io.refrax.tenant.TenantProvisioningService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/tenants")
public class TenantController {

    @Inject
    TenantProvisioningService provisioningService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createTenant(@Valid CreateTenantRequest req) {
        try {
            provisioningService.provisionTenant(req.schema());
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}
