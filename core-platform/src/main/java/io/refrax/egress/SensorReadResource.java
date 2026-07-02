package io.refrax.egress;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;

@Path("v1/sensors")
public class SensorReadResource {

    @Inject
    io.vertx.mutiny.pgclient.PgPool client;

    @GET
    @Path("{sensorId}/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> latest(@PathParam("sensorId") String sensorId) {
        return client.preparedQuery(
                        "select payload, valid_time from events " +
                                "where event_type = 'AirQualityReading' " +
                                "  and payload ->> 'sensorId' = $1 " +
                                "order by seq desc limit 1")
                .execute(Tuple.of(sensorId))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    Row row = rows.iterator().next();
                    JsonObject payload = (JsonObject) row.getValue("payload");
                    OffsetDateTime validTime = row.getOffsetDateTime("valid_time");

                    ExposableReading view = project(payload, validTime);

                    return Response.ok(JsonObject.mapFrom(view)).build();
                });
    }

    private ExposableReading project(JsonObject payload, OffsetDateTime validTime) {
        return new ExposableReading(
                payload.getString("sensorId"),
                payload.getString("metric"),
                payload.getDouble("value"),
                payload.getString("unit"),
                validTime
        );
    }

    public record ExposableReading(
            String sensorId, String metric, double value, String unit, OffsetDateTime observedAt) {}
}
