package io.refrax.readmodel;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P4 core: the read side is a persisted, incremental, disposable, replayable projection that
 * is decoupled from ingest.
 */
@QuarkusTest
class ReadModelConsumerTest {

    @Inject
    ReadModelConsumer consumer;

    @Inject
    PgPool client;

    private long post(String sensorId, double value) {
        String event = """
                { "type": "AirQualityReading", "eventId": "%s", "observedAt": "2026-07-02T10:05:00Z",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                               "deviceDbId": 999999, "userId": "u-1" } }
                """.formatted(UUID.randomUUID(), sensorId, value);
        return given().contentType("application/json").body(event)
                .when().post("/v1/events").then().statusCode(202)
                .extract().jsonPath().getLong("seq");
    }

    private float latest(String sensorId) {
        return given().queryParam("sensor", sensorId)
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(200)
                .extract().jsonPath().getFloat("value");
    }

    @Test
    void readModelResultMatchesAnOnDemandFoldOfTheLog() {
        String sensor = "sensor-" + UUID.randomUUID();
        post(sensor, 10.0);
        post(sensor, 20.0);
        consumer.catchUp().await().indefinitely();

        // Fold the log directly (the "old" way) for the latest value...
        RowSet<Row> rows = client.preparedQuery(
                        "select payload from events where event_type = 'AirQualityReading' "
                                + "and payload ->> 'sensorId' = $1 order by seq desc limit 1")
                .execute(Tuple.of(sensor)).await().indefinitely();
        double fromLog = ((JsonObject) rows.iterator().next().getValue("payload")).getDouble("value");

        // ...and it matches the read-model-backed GET (the "new" way).
        assertEquals((float) fromLog, latest(sensor));
        assertEquals(20.0f, latest(sensor));
    }

    @Test
    void readModelIsDisposableAndRebuildsIdenticallyOnFullReplay() {
        String sensor = "sensor-" + UUID.randomUUID();
        post(sensor, 42.0);
        consumer.catchUp().await().indefinitely();
        float before = latest(sensor);

        // Delete the read models entirely and rebuild from seq 0.
        consumer.replayAll().await().indefinitely();

        assertEquals(before, latest(sensor));
        assertEquals(42.0f, latest(sensor));
    }

    @Test
    void targetedReplayReprojectsOneEntityWithoutTouchingOthers() {
        String a = "sensor-" + UUID.randomUUID();
        String b = "sensor-" + UUID.randomUUID();
        post(a, 1.0);
        post(b, 2.0);
        consumer.catchUp().await().indefinitely();

        // A newer event for A arrives but the consumer has NOT caught up yet.
        post(a, 1.5);

        // Reproject only A from the log — B must be untouched, and no full rebuild happens.
        consumer.reprojectEntity("AirQualityReading", "sensorId", a).await().indefinitely();

        assertEquals(1.5f, latest(a));
        assertEquals(2.0f, latest(b));
    }

    @Test
    void readModelOutageDoesNotBlockIngest() {
        String sensor = "sensor-" + UUID.randomUUID();

        // Simulate a read-model outage.
        client.query("drop table if exists reading_latest").execute().await().indefinitely();

        // Ingest still succeeds — the log is the source of truth and must always be writable.
        post(sensor, 7.7);

        // The projection recovers and catches up.
        consumer.catchUp().await().indefinitely();
        assertEquals(7.7f, latest(sensor));
    }
}
