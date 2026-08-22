package io.refrax.egress;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts all invalid query-parameter errors into a consistent Bad Request JSON response.
 */
@ApplicationScoped
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    /**
     * Maps an invalid request condition to a 400 response with the original exception message.
     *
     * @param exception the thrown validation or parsing exception
     * @return the HTTP 400 response payload
     */
    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Status.BAD_REQUEST)
                .entity(new JsonObject().put("error", exception.getMessage()))
                .build();
    }
}
