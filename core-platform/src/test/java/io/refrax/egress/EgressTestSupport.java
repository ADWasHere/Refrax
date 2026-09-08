package io.refrax.egress;

import io.quarkus.vertx.VertxContextSupport;
import io.refrax.readmodel.ReadModelConsumer;
import io.refrax.tenant.TestTenantScope;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;

public abstract class EgressTestSupport {

    @Inject
    protected ReadModelConsumer consumer;

    @Inject
    protected TestTenantScope tenantScope;

    /**
     * Bridges a reactive Panache operation onto a Vert.x context and blocks for the result. A
     * {@code @QuarkusTest} method runs on the main thread, where reactive Panache has no context;
     * the {@link Uni} is built inside the supplier so its transaction binds to that context. Since
     * that context carries no HTTP request either, {@link TestTenantScope} gives it its own tenant
     * (matching the "public" schema the tests ingest into via the header-resolved HTTP calls above).
     */
    protected <T> T await(Supplier<Uni<T>> action) {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> tenantScope.run("public", action));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    protected String ingestReading(String sensorId, double value) {
        String event = """
                {
                  "eventType": "AirQualityReading",
                  "eventId": "%s",
                  "observedAt": "2026-07-02T10:05:00Z",
                  "payload": {
                    "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                    "deviceDbId": 999999, "userId": "u-123", "partitionKey": "shard-7"
                  }
                }
                """.formatted(UUID.randomUUID(), sensorId, value);
        given().contentType("application/json")
                .header("X-Tenant-ID", "public")
                .body(event)
                .when().post("/v1/events").then().statusCode(202);
        await(() -> consumer.catchUp());
        return sensorId;
    }

    protected void ingestAt(String sensorId, String observedAt, double value) {
        String event = """
                { "eventType": "AirQualityReading", "eventId": "%s", "observedAt": "%s",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3" } }
                """.formatted(UUID.randomUUID(), observedAt, sensorId, value);
        given().contentType("application/json")
                .header("X-Tenant-ID", "public")
                .body(event)
                .when().post("/v1/events").then().statusCode(202);
    }

    protected long ingest(String sensorId, double value) {
        String event = """
                {
                 "eventType": "AirQualityReading",
                 "eventId": "%s",
                 "observedAt": "2026-07-02T10:05:00Z",
                 "payload": {
                   "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                   "deviceDbId": 999999, "userId": "u-123", "partitionKey": "shard-7"
                 }
                }
                """.formatted(UUID.randomUUID(), sensorId, value);
        long seq = given().contentType("application/json")
                .header("X-Tenant-ID", "public")
                .body(event)
                .when().post("/v1/events").then().statusCode(202)
                .extract().jsonPath().getLong("seq");
        await(() -> consumer.catchUp());
        return seq;
    }
}
