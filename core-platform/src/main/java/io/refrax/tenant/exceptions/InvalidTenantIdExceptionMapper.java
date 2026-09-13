package io.refrax.tenant.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Without this, a malformed tenant identifier (caught by
 * {@code TenantAwarePanache.requireSafeSchema}, which exists to keep it out of a raw SQL
 * statement) surfaced as a bare, unmapped exception — an opaque framework 500 instead of a
 * deliberate 400 naming the actual problem.
 */
@Provider
public class InvalidTenantIdExceptionMapper implements ExceptionMapper<InvalidTenantIdException> {

    private static final Logger LOG = Logger.getLogger(InvalidTenantIdExceptionMapper.class);

    @Override
    public Response toResponse(InvalidTenantIdException exception) {
        LOG.warnf("Rejected request: %s", exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_TENANT_ID", exception.getMessage()))
                .build();
    }

    public record ErrorResponse(String code, String message) {}
}
