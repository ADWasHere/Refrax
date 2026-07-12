package io.refrax.readmodel;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Operational endpoints to drive the read-model consumer by hand: rebuild everything from the
 * log, or re-derive a single entity. For testing/operations only — these are NOT secured yet
 * (auth is a later concern), so do not expose them publicly in production.
 */
@Path("v1/admin/readmodel")
public class ReadModelAdminResource {

    @Inject
    ReadModelConsumer consumer;

    /** Full replay: wipe both read models + cursor, rebuild from seq 0. */
    @POST
    @Path("replay")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> replayAll() {
        return consumer.replayAll()
                .map(throughSeq -> Response.ok(
                        new JsonObject().put("status", "rebuilt").put("throughSeq", throughSeq)).build());
    }

    /** Targeted replay: re-derive one entity's current-state row from the log, touching nothing else. */
    @POST
    @Path("reproject")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> reproject(@QueryParam("eventType") String eventType,
                                   @QueryParam("identityField") String identityField,
                                   @QueryParam("identity") String identity) {
        if (isBlank(eventType) || isBlank(identityField) || isBlank(identity)) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JsonObject().put("error",
                            "eventType, identityField and identity are all required")).build());
        }
        return consumer.reprojectEntity(eventType, identityField, identity)
                .map(v -> Response.ok(
                        new JsonObject().put("status", "reprojected").put("identity", identity)).build());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
