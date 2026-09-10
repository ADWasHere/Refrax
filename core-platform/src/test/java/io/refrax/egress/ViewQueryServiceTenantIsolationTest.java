package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import io.refrax.tenant.TenantProvisioningService;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Checks whether wrapping the raw {@code PgPool} query in {@code TenantAwarePanache.withSession}
 * actually scopes it to the tenant's schema. {@code TenantAwarePanache} pins the schema via
 * {@code SET LOCAL search_path} on a connection borrowed through the Hibernate Reactive session —
 * but {@code client.preparedQuery(...)} borrows its own connection directly from the raw pool. If
 * those are two independent connections, the pin has no effect on the actual query.
 */
@QuarkusTest
class ViewQueryServiceTenantIsolationTest {

    @Inject
    TenantProvisioningService provisioningService;

    @Inject
    PgPool client;

    @Test
    void latestOnlySeesRowsFromItsOwnTenantSchema() throws Exception {
        String tenantA = "view_test_a_" + Long.toHexString(System.nanoTime());
        String tenantB = "view_test_b_" + Long.toHexString(System.nanoTime());
        provisioningService.provisionTenant(tenantA);
        provisioningService.provisionTenant(tenantB);

        insertReadingLatest(tenantA, "sensor-a", "AirQualityReading", 111.0);
        insertReadingLatest(tenantB, "sensor-b", "AirQualityReading", 222.0);

        // Tenant A's request must see ONLY its own row (sensor-a), never tenant B's.
        given().header("X-Tenant-ID", tenantA)
                .queryParam("sensor", "sensor-a")
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(200)
                .body("value", org.hamcrest.Matchers.is(111.0f));

        // Tenant A's request for tenant B's sensor id must NOT find it (proves no leakage/mixup).
        given().header("X-Tenant-ID", tenantA)
                .queryParam("sensor", "sensor-b")
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(404);
    }

    private void insertReadingLatest(String schema, String sensorId, String eventType, double value) {
        String entityId = "urn:refrax:" + eventType + ":" + sensorId;
        String exposedJson = "{\"sensorId\":\"" + sensorId + "\",\"metric\":\"PM2.5\",\"value\":" + value + ",\"unit\":\"ug/m3\"}";
        client.preparedQuery("INSERT INTO \"" + schema + "\".reading_latest "
                        + "(event_type, entity_id, exposed_json, observed_at, seq) VALUES ($1, $2, $3::jsonb, now(), 1)")
                .execute(io.vertx.mutiny.sqlclient.Tuple.of(eventType, entityId, exposedJson))
                .await().indefinitely();
    }
}
