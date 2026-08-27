package io.refrax.egress;

import io.refrax.readmodel.ReadModelConsumer;
import jakarta.inject.Inject;

import java.util.UUID;

import static io.restassured.RestAssured.given;

public abstract class EgressTestSupport {

    @Inject
    protected ReadModelConsumer consumer;

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
                .header("X-Tenant-ID", "test-tenant")
                .body(event)
                .when().post("/v1/events").then().statusCode(202);
        consumer.catchUp().await().indefinitely();
        return sensorId;
    }

    protected void ingestAt(String sensorId, String observedAt, double value) {
        String event = """
                { "eventType": "AirQualityReading", "eventId": "%s", "observedAt": "%s",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3" } }
                """.formatted(UUID.randomUUID(), observedAt, sensorId, value);
        given().contentType("application/json")
                .header("X-Tenant-ID", "test-tenant")
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
                .header("X-Tenant-ID", "test-tenant")
                .body(event)
                .when().post("/v1/events").then().statusCode(202)
                .extract().jsonPath().getLong("seq");
        consumer.catchUp().await().indefinitely();
        return seq;
    }
}
