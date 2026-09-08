package io.refrax.readmodel;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.VertxContextSupport;
import io.refrax.tenant.TestTenantScope;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P4 core: the read side is a persisted, incremental, disposable, replayable projection that
 * is decoupled from ingest.
 *
 * <p>The read-model schema is owned by Flyway (see {@code db/migration}) and applied on
 * startup, so these tests hold no DDL of their own and touch persistence only through the
 * Panache-backed {@link ReadModelStore}.
 */
@QuarkusTest
class ReadModelConsumerTest {

    @Inject
    ReadModelConsumer consumer;

    @Inject
    ReadModelStore store;

    @Inject
    TestTenantScope tenantScope;

    /**
     * Reactive Panache operations must run on a Vert.x context; a {@code @QuarkusTest} method runs
     * on the main thread, so bridge to one and block for the result. The {@link Uni} is built
     * inside the supplier so its transaction binds to that context, not to the main thread. That
     * context carries no HTTP request either, so {@link TestTenantScope} gives it its own tenant
     * (the "public" schema the tests ingest into via the header-resolved REST calls below).
     * Ingest/read assertions go through the blocking REST API and stay on the main thread.
     */
    private <T> T await(Supplier<Uni<T>> action) {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> tenantScope.run("public", action));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private long post(String sensorId, double value) {
        String event = """
                { "eventType": "AirQualityReading", "eventId": "%s", "observedAt": "2026-07-02T10:05:00Z",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                               "deviceDbId": 999999, "userId": "u-1" } }
                """.formatted(UUID.randomUUID(), sensorId, value);
        return given().contentType("application/json")
                .header("X-Tenant-ID", "public")
                .body(event)
                .when().post("/v1/events").then().statusCode(202)
                .extract().jsonPath().getLong("seq");
    }

    private float latest(String sensorId) {
        return given().header("X-Tenant-ID", "public")
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
        await(() -> consumer.catchUp());

        // Fold the log directly (the "old" way) for the latest value...
        JournalEntry fromLog = await(() -> store.findLatestEvent("AirQualityReading", "sensorId", sensor));
        double folded = fromLog.payload().getDouble("value");

        // ...and it matches the read-model-backed GET (the "new" way).
        assertEquals((float) folded, latest(sensor));
        assertEquals(20.0f, latest(sensor));
    }

    @Test
    void readModelIsDisposableAndRebuildsIdenticallyOnFullReplay() {
        String sensor = "sensor-" + UUID.randomUUID();
        post(sensor, 42.0);
        await(() -> consumer.catchUp());
        float before = latest(sensor);

        // Delete the read models entirely and rebuild from seq 0.
        await(() -> consumer.replayAll());

        assertEquals(before, latest(sensor));
        assertEquals(42.0f, latest(sensor));
    }

    @Test
    void targetedReplayReprojectsOneEntityWithoutTouchingOthers() {
        String a = "sensor-" + UUID.randomUUID();
        String b = "sensor-" + UUID.randomUUID();
        post(a, 1.0);
        post(b, 2.0);
        await(() -> consumer.catchUp());

        // A newer event for A arrives but the consumer has NOT caught up yet.
        post(a, 1.5);

        // Reproject only A from the log — B must be untouched, and no full rebuild happens.
        await(() -> consumer.reprojectEntity("AirQualityReading", "sensorId", a));

        assertEquals(1.5f, latest(a));
        assertEquals(2.0f, latest(b));
    }

    @Test
    void readModelOutageDoesNotBlockIngest() {
        String sensor = "sensor-" + UUID.randomUUID();

        // Simulate a read-model outage by wiping both read models and the cursor.
        await(() -> store.reset());

        // Ingest still succeeds — the log is the source of truth and must always be writable.
        post(sensor, 7.7);

        // The projection recovers and catches up once it runs again.
        await(() -> consumer.catchUp());
        assertEquals(7.7f, latest(sensor));
    }
}
