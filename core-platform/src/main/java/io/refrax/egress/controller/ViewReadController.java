package io.refrax.egress.controller;

import io.refrax.egress.ViewQueryService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.*;

/**
 * REST controller for the view-based read API.
 *
 * <p>This endpoint exposes the latest, stream and time-series read operations
 */
@Path("v1/views")
public class ViewReadController {

    @Inject
    ViewQueryService viewQueryService;

    /**
     * Returns the current latest state for a view and optional axis filters.
     *
     * @param viewName the view name
     * @param uriInfo the incoming request metadata and query parameters
     * @return the latest entity response
     */
    @GET
    @Path("{view}/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> latest(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        return viewQueryService.latest(viewName, uriInfo.getQueryParameters());
    }

    /**
     * Streams all future series rows after the provided sequence offset.
     *
     * @param viewName the view name
     * @param uriInfo the incoming request metadata and query parameters
     * @return the result slice as a JSON array
     */
    @GET
    @Path("{view}/stream")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> stream(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
        long after = parseLong(query.getFirst("after"), 0L);
        return viewQueryService.stream(viewName, query.getFirst("format"), after);
    }

    /**
     * Returns a filtered time-series slice for a view.
     *
     * @param viewName the view name
     * @param uriInfo the incoming request metadata and query parameters
     * @return the series response payload
     */
    @GET
    @Path("{view}/series")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> series(@PathParam("view") String viewName, @Context UriInfo uriInfo) {
        return viewQueryService.series(viewName, uriInfo.getQueryParameters());
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
