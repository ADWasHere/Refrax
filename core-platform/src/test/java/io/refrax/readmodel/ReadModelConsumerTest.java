package io.refrax.readmodel;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void ensureReadModelSchemaExists() {
        client.query("""
                create table if not exists projection_cursor (
                    projection text primary key,
                    position bigint not null
                );
                create table if not exists reading_latest (
                    event_type text not null,
                    entity_id text not null,
                    exposed_json jsonb not null,
                    observed_at timestamptz,
                    seq bigint not null,
                    primary key (event_type, entity_id)
                );
                create table if not exists reading_series (
                    event_type text not null,
                    seq bigint not null,
                    entity_id text not null,
                    exposed_json jsonb not null,
                    observed_at timestamptz not null,
                    primary key (event_type, seq, observed_at)
                );
                select create_hypertable('reading_series', 'observed_at', if_not_exists => true, migrate_data => true);
                create index if not exists reading_series_entity_time
                    on reading_series (event_type, entity_id, observed_at desc);
                """).execute().await().indefinitely();
    }

    private long post(String sensorId, double value) {
        String event = """
                { "eventType": "AirQualityReading", "eventId": "%s", "observedAt": "2026-07-02T10:05:00Z",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                               "deviceDbId": 999999, "userId": "u-1" } }
                """.formatted(UUID.randomUUID(), sensorId, value);
        return given().contentType("application/json")
                .header("X-Tenant-ID", "test-tenant")
                .body(event)
                .when().post("/v1/events").then().statusCode(202)
                .extract().jsonPath().getLong("seq");
    }

    private float latest(String sensorId) {
        return given().header("X-Tenant-ID", "test-tenant")
                .queryParam("sensor", sensorId)
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

        // Simulate a read-model outage by dropping the tables that power the projection.
        client.query("drop table if exists reading_latest; drop table if exists reading_series; delete from projection_cursor")
                .execute().await().indefinitely();

        // Ingest still succeeds — the log is the source of truth and must always be writable.
        post(sensor, 7.7);

        // The projection recovers and catches up after the read-model schema is restored.
        ensureReadModelSchemaExists();
        consumer.catchUp().await().indefinitely();
        assertEquals(7.7f, latest(sensor));
    }
}
