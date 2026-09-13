package io.refrax.tenant.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class TenantNotFoundExceptionMapper implements ExceptionMapper<TenantNotFoundException> {

    private static final Logger LOG = Logger.getLogger(TenantNotFoundExceptionMapper.class);

    @Override
    public Response toResponse(TenantNotFoundException exception) {
        LOG.warnf("Rejected request: %s", exception.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("TENANT_NOT_FOUND", exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String code, String message) {}
}
