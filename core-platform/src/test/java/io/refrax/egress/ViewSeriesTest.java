package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import io.refrax.readmodel.ReadModelConsumer;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The time-series read model ({@code reading_series}, the plain-Postgres stand-in for a
 * Timescale hypertable) answers a valid-time window query per identity — served from the
 * purpose-built, time-indexed structure, not by folding the seq-ordered log.
 */
@QuarkusTest
class ViewSeriesTest {

    @Inject
    ReadModelConsumer consumer;

    private void ingestAt(String sensorId, String observedAt, double value) {
        String event = """
                { "type": "AirQualityReading", "eventId": "%s", "observedAt": "%s",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3" } }
                """.formatted(UUID.randomUUID(), observedAt, sensorId, value);
        given().contentType("application/json").body(event)
                .when().post("/v1/events").then().statusCode(202);
    }

    @Test
    void seriesReturnsTheValidTimeWindowForASensor() {
        String sensor = "sensor-" + UUID.randomUUID();
        ingestAt(sensor, "2026-07-02T10:00:00Z", 1.0);
        ingestAt(sensor, "2026-07-02T10:05:00Z", 2.0);
        ingestAt(sensor, "2026-07-02T10:10:00Z", 3.0);
        consumer.catchUp().await().indefinitely();

        JsonPath series = given()
                .queryParam("sensor", sensor)
                .queryParam("from", "2026-07-02T10:04:00Z")
                .queryParam("to", "2026-07-02T10:11:00Z")
                .when()
                .get("/v1/views/air-quality-full/series")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        // Only the two readings inside the window, in valid-time order.
        List<Float> values = series.getList("event.value", Float.class);
        assertEquals(List.of(2.0f, 3.0f), values);
    }
}
