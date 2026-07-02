package io.refrax.ingestion;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Path("v1/events")
public class EventIngestResource {

    @Inject
    io.vertx.mutiny.pgclient.PgPool client;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> append(JsonObject event) {
        String type = event.getString("type");
        JsonObject payload = event.getJsonObject("payload");

        String eventIdStr = event.getString("eventId");
        UUID eventId = (eventIdStr != null) ? UUID.fromString(eventIdStr) : UUID.randomUUID();

        String observedAt = event.getString("observedAt");
        OffsetDateTime validTime = (observedAt != null) ? OffsetDateTime.parse(observedAt) : null;

        String tenantId = "default"; //TODO Enable later tenants with keys for deleting
        String schemaVersion = "v1";

        return client.preparedQuery(
                        "insert into events (tenant_id, event_type, payload, valid_time, event_id, schema_version) " +
                                "values ($1, $2, $3, $4, $5, $6) " +
                                "on conflict (tenant_id, event_id) do nothing " +
                                "returning seq")
                .execute(Tuple.of(tenantId, type, payload, validTime, eventId, schemaVersion))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        return Response.accepted().entity(Map.of("status", "duplicate")).build();
                    }
                    Long seq = rows.iterator().next().getLong("seq");
                    return Response.accepted().entity(Map.of("seq", seq)).build();
                });
    }
}
