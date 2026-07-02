package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * End-to-end view behaviour over HTTP: a view shrinks under the schema in every format, the
 * same view renders in any format without view-specific projector code (the matrix), and a
 * query on an undeclared axis is rejected. The gate's leak guarantee still holds under views.
 */
@QuarkusTest
class ViewReadResourceTest {

    private String ingestReading(String sensorId, double value) {
        String event = """
                {
                  "type": "AirQualityReading",
                  "eventId": "%s",
                  "observedAt": "2026-07-02T10:05:00Z",
                  "payload": {
                    "sensorId": "%s", "metric": "PM2.5", "value": %s, "unit": "ug/m3",
                    "deviceDbId": 999999, "userId": "u-123", "partitionKey": "shard-7"
                  }
                }
                """.formatted(UUID.randomUUID(), sensorId, value);
        given().contentType("application/json").body(event)
                .when().post("/v1/events").then().statusCode(202);
        return sensorId;
    }

    @Test
    void valueOnlyViewHidesMetricInNative() {
        String sensorId = ingestReading("sensor-" + UUID.randomUUID(), 14.7);

        given()
                .queryParam("sensor", sensorId)
                .when()
                .get("/v1/views/air-quality-value-only/latest")
                .then()
                .statusCode(200)
                .body("value", equalTo(14.7f))
                .body("unit", equalTo("ug/m3"))
                // The view shrinks under the schema: metric is exposable, but not in this view.
                .body("$", not(hasKey("metric")))
                // Leak guarantee still holds under a view.
                .body("$", not(hasKey("deviceDbId")))
                .body("$", not(hasKey("userId")));
    }

    @Test
    void valueOnlyViewHidesMetricInNgsiLd() {
        String sensorId = ingestReading("sensor-" + UUID.randomUUID(), 14.7);

        // Same view, NGSI-LD format: same shrunk subset, only the shape differs. No
        // view-specific projector code — this is the view × format matrix.
        given()
                .queryParam("sensor", sensorId)
                .queryParam("format", "ngsi-ld")
                .when()
                .get("/v1/views/air-quality-value-only/latest")
                .then()
                .statusCode(200)
                .body("numericValue.value", equalTo(14.7f))
                .body("unit.value", equalTo("ug/m3"))
                .body("$", not(hasKey("metric")))
                .body("$", not(hasKey("deviceDbId")));
    }

    @Test
    void fullViewExposesMetric() {
        String sensorId = ingestReading("sensor-" + UUID.randomUUID(), 14.7);

        given()
                .queryParam("sensor", sensorId)
                .when()
                .get("/v1/views/air-quality-full/latest")
                .then()
                .statusCode(200)
                .body("metric", equalTo("PM2.5"))
                .body("value", equalTo(14.7f));
    }

    @Test
    void queryOnUndeclaredAxisIsRejected() {
        // metric is neither exposed nor a declared axis in the value-only view: filtering on
        // it must be refused, not silently return nothing (which would leak its existence).
        given()
                .queryParam("metric", "PM2.5")
                .when()
                .get("/v1/views/air-quality-value-only/latest")
                .then()
                .statusCode(400);
    }

    @Test
    void unknownViewIsRejected() {
        given()
                .queryParam("sensor", "whatever")
                .when()
                .get("/v1/views/does-not-exist/latest")
                .then()
                .statusCode(400);
    }

    @Test
    void unknownFormatIsRejected() {
        given()
                .queryParam("sensor", "whatever")
                .queryParam("format", "xml")
                .when()
                .get("/v1/views/air-quality-full/latest")
                .then()
                .statusCode(400);
    }

    @Test
    void unmatchedIdentityYieldsNotFound() {
        given()
                .queryParam("sensor", "sensor-" + UUID.randomUUID())
                .when()
                .get("/v1/views/air-quality-full/latest")
                .then()
                .statusCode(404);
    }
}
