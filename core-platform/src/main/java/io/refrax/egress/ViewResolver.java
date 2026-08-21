package io.refrax.egress;

import io.refrax.projection.Projector;
import io.refrax.projection.ProjectorRegistry;
import io.refrax.view.ViewBinding;
import io.refrax.view.ViewBindingRegistry;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Resolves an incoming view request against the configured view-binding registry and projector registry.
 *
 * <p>This component is intentionally narrow in scope: it validates the requested view/format pair and
 * returns the bound schema and projector needed by the query service. Invalid input fails fast with a
 * 400 Bad Request response as a {@link WebApplicationException}.
 */
@ApplicationScoped
public class ViewResolver {

    @Inject
    ViewBindingRegistry bindings;

    @Inject
    ProjectorRegistry projectors;

    /**
     * Resolves the named view and output format to a bound schema and projector.
     *
     * @param viewName the requested view name
     * @param requestedFormat the requested output format; defaults to {@code native} when empty
     * @return the resolved binding and projector
     * @throws WebApplicationException when the view or format is unknown or invalid
     */
    public Resolved resolve(String viewName, String requestedFormat) {
        if (viewName == null || viewName.isBlank()) {
            throw badRequest("View name parameter cannot be blank");
        }

        ViewBinding binding = bindings.find(viewName).orElseThrow(() -> badRequest("Unknown view: " + viewName));

        String format = (requestedFormat == null || requestedFormat.isBlank()) ? "native" : requestedFormat;
        Projector projector = projectors.byFormat(format).orElseThrow(() -> badRequest("Unknown format: " + format));

        return new Resolved(binding, projector);
    }

    private static WebApplicationException badRequest(String message) {
        return new WebApplicationException(
                Response.status(Status.BAD_REQUEST)
                        .entity(new JsonObject().put("error", message))
                        .build());
    }

    public record Resolved(ViewBinding binding, Projector projector) {
    }
}
