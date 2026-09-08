package io.refrax.tenant.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TenantNotFoundExceptionMapper implements ExceptionMapper<TenantNotFoundException> {

    @Override
    public Response toResponse(TenantNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("TENANT_NOT_FOUND", exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String code, String message) {}
}
