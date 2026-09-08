package io.refrax.readmodel;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.VertxContextSupport;
import io.refrax.tenant.TenantProvisioningService;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises {@link ReadModelConsumer#tick()} itself — the real scheduled entry point — against two
 * genuinely separate, freshly provisioned tenant schemas, proving the tenant fan-out catches both
 * up in one firing without cross-tenant interference. Complements
 * {@link TenantJobRunnerConcurrencyTest}, which isolates the underlying Arc mechanism with a
 * synthetic probe; this one proves the real production code path end to end, since a scheduled
 * {@code tick()} firing has the same "one outer subscription, several inner activations" shape
 * that the synthetic test found hazardous under concurrency.
 */
@QuarkusTest
class ReadModelConsumerTickMultiTenantTest {

    @Inject
    ReadModelConsumer consumer;

    @Inject
    TenantProvisioningService provisioningService;

    @Inject
    PgPool client;

    private static <T> T await(Supplier<Uni<T>> action) {
        try {
            return VertxContextSupport.subscribeAndAwait(action::get);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Reads {@code reading_latest} directly, schema-qualified, bypassing ViewQueryService (which
     * still queries the shared PgPool unscoped and would not find per-tenant-schema rows regardless
     * of whether tick() worked — that gap is separate from what this test is proving).
     */
    private long countReadingLatestRowsInSchema(String schema) {
        return client.preparedQuery("SELECT count(*) FROM \"" + schema + "\".reading_latest")
                .execute().await().indefinitely()
                .iterator().next().getLong(0);
    }

    private void post(String tenant, String sensorId, double value) {
        String event = """
                { "eventType": "AirQualityReading", "eventId": "%s", "observedAt": "2026-07-02T10:05:00Z",
                  "payload": { "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                               "deviceDbId": 999999, "userId": "u-1" } }
                """.formatted(UUID.randomUUID(), sensorId, value);
        given().contentType("application/json")
                .header("X-Tenant-ID", tenant)
                .body(event)
                .when().post("/v1/events").then().statusCode(202);
    }

    @Test
    void tickCatchesUpAllTenantSchemasInOneFiring() throws Exception {
        String tenantA = "tick_test_a_" + Long.toHexString(System.nanoTime());
        String tenantB = "tick_test_b_" + Long.toHexString(System.nanoTime());
        provisioningService.provisionTenant(tenantA);
        provisioningService.provisionTenant(tenantB);

        post(tenantA, "sensor-" + UUID.randomUUID(), 11.0);
        post(tenantB, "sensor-" + UUID.randomUUID(), 22.0);

        // One real scheduler firing must catch up BOTH tenants, into their OWN schema, without
        // throwing and without cross-tenant interference.
        await(() -> consumer.tick());

        assertEquals(1, countReadingLatestRowsInSchema(tenantA));
        assertEquals(1, countReadingLatestRowsInSchema(tenantB));
    }
}
