package io.refrax.ingestion.controller;

import io.refrax.ingestion.Events;
import io.refrax.ingestion.IncomingEvent;
import io.refrax.ingestion.IncomingEventValidator;
import io.refrax.tenant.TenantAwarePanache;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hibernate.exception.ConstraintViolationException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Path("v1/events")
public class EventIngestController {

    @Inject
    IncomingEventValidator incomingEventValidator;

    @Inject
    TenantAwarePanache panache;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> append(JsonObject event) {
        Objects.requireNonNull(event, "event");

        IncomingEvent incomingEvent = incomingEventValidator.from(event);

        String eventType = incomingEvent.eventType();
        Map<String, Object> mutableFields = new java.util.HashMap<>(incomingEvent.fields());
        JsonObject payload = new JsonObject(mutableFields);
        payload.remove("tenant");
        payload.remove("tenantId");

        OffsetDateTime validTime = OffsetDateTime.ofInstant(incomingEvent.occurredAt(), java.time.ZoneOffset.UTC);
        String eventIdStr = event.getString("eventId");
        UUID eventId = (eventIdStr != null) ? UUID.fromString(eventIdStr) : UUID.randomUUID();
        String schemaVersion = "v1";

        return panache.withTransaction(() -> Events.findByEventId(eventId)
                        .flatMap(existing -> {
                            if (existing != null) {
                                return Uni.createFrom().item(Response.accepted().entity(Map.of("status", "duplicate")).build());
                            }

                            Events entity = new Events();
                            entity.eventType = eventType;
                            entity.payload = payload.encode();
                            entity.validTime = validTime;
                            entity.eventId = eventId;
                            entity.schemaVersion = schemaVersion;

                            return entity.persistAndFlush()
                                    .map(saved -> {
                                        Events savedEvent = (Events) saved;
                                        return Response.accepted().entity(Map.of("seq", savedEvent.seq)).build();
                                    });
                        }))
                .onFailure(ConstraintViolationException.class)
                .recoverWithItem(throwable -> Response.accepted().entity(Map.of("status", "duplicate")).build());
    }
}
