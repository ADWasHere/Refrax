package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Input-handling contract of the identity-driven latest endpoint
 * {@code GET /v1/views/{view}/latest}: unknown views and missing identity parameters are
 * rejected before any query runs, and an unmatched identity yields 404. The happy path
 * (schema-driven projection + gate guarantee) is covered by {@link GateProofTest}.
 */
@QuarkusTest
class ViewReadResourceTest {

    @Test
    void unknownViewIsRejected() {
        given()
                .queryParam("sensorId", "whatever")
                .when()
                .get("/v1/views/DoesNotExist/latest")
                .then()
                .statusCode(400);
    }

    @Test
    void missingIdentityParameterIsRejected() {
        given()
                .when()
                .get("/v1/views/AirQualityReading/latest")
                .then()
                .statusCode(400);
    }

    @Test
    void unmatchedIdentityYieldsNotFound() {
        given()
                .queryParam("sensorId", "sensor-" + UUID.randomUUID())
                .when()
                .get("/v1/views/AirQualityReading/latest")
                .then()
                .statusCode(404);
    }
}
