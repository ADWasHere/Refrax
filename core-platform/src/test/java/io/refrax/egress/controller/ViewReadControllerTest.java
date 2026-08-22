package io.refrax.egress.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.refrax.egress.EgressTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class ViewReadControllerTest extends EgressTestSupport {

    @Test
    void valueOnlyViewHidesMetricInNative() {
        String sensorId = ingestReading("sensor-" + UUID.randomUUID(), 14.7);

        given().queryParam("sensor", sensorId)
                .when().get("/v1/views/air-quality-value-only/latest")
                .then().statusCode(200)
                .body("value", equalTo(14.7f))
                .body("unit", equalTo("ug/m3"))
                .body("$", not(hasKey("metric")))
                .body("$", not(hasKey("deviceDbId")))
                .body("$", not(hasKey("userId")));
    }

    @Test
    void valueOnlyViewHidesMetricInNgsiLd() {
        String sensorId = ingestReading("sensor-" + UUID.randomUUID(), 14.7);

        given().queryParam("sensor", sensorId)
                .queryParam("format", "ngsi-ld")
                .when().get("/v1/views/air-quality-value-only/latest")
                .then().statusCode(200)
                .body("numericValue.value", equalTo(14.7f))
                .body("unit.value", equalTo("ug/m3"))
                .body("$", not(hasKey("metric")))
                .body("$", not(hasKey("deviceDbId")));
    }

    @Test
    void fullViewExposesMetric() {
        String sensorId = ingestReading("sensor-" + UUID.randomUUID(), 14.7);

        given().queryParam("sensor", sensorId)
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(200)
                .body("metric", equalTo("PM2.5"))
                .body("value", equalTo(14.7f));
    }

    @Test
    void queryOnUndeclaredAxisIsRejected() {
        given().queryParam("metric", "PM2.5")
                .when().get("/v1/views/air-quality-value-only/latest")
                .then().statusCode(400);
    }

    @Test
    void unknownViewIsRejected() {
        given().queryParam("sensor", "whatever")
                .when().get("/v1/views/does-not-exist/latest")
                .then().statusCode(400);
    }

    @Test
    void unknownFormatIsRejected() {
        given().queryParam("sensor", "whatever")
                .queryParam("format", "xml")
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(400);
    }

    @Test
    void unmatchedIdentityYieldsNotFound() {
        given().queryParam("sensor", "sensor-" + UUID.randomUUID())
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(404);
    }
}
