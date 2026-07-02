package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * The gate's core guarantee: internal payload fields never leak into an exposed
 * projection.
 *
 * <p>Ingest an event whose payload is deliberately polluted with internal fields
 * ({@code deviceDbId}, {@code userId}, {@code partitionKey}) and assert they are
 * <b>absent</b> from the projected GET output. The guarantee is structural: the gate
 * ({@code io.refrax.gate.Gate}) copies only declared exposable fields into the typed
 * {@code ExposableEntity}, so an internal field cannot appear in the output — the value
 * handed to the response simply does not contain one. None of the internal fields are
 * even declared in the schema, so deny-by-default alone excludes them.
 *
 * <p>Event in, only the exposable part out. The test talks to the app over HTTP only;
 * the events table is created by the Dev Services init script (see
 * {@code src/test/resources/db/init.sql}).
 */
@QuarkusTest
class GateProofTest {

    @Test
    void internalFieldsNeverLeakIntoProjection() {
        String sensorId = "sensor-" + UUID.randomUUID();

        // An event whose payload carries both exposable fields and internal pollution.
        String pollutedEvent = """
                {
                  "type": "AirQualityReading",
                  "eventId": "%s",
                  "observedAt": "2026-07-02T10:00:00Z",
                  "payload": {
                    "sensorId": "%s",
                    "metric": "PM2.5",
                    "value": 12.3,
                    "unit": "ug/m3",
                    "deviceDbId": 999999,
                    "userId": "internal-user-1",
                    "partitionKey": "shard-7"
                  }
                }
                """.formatted(UUID.randomUUID(), sensorId);

        // Event in.
        given()
                .contentType("application/json")
                .body(pollutedEvent)
                .when()
                .post("/v1/events")
                .then()
                .statusCode(202);

        // Only the exposable part out. The identity field name (sensorId) is read from the
        // schema by the handler, not hardcoded.
        given()
                .queryParam("sensorId", sensorId)
                .when()
                .get("/v1/views/AirQualityReading/latest")
                .then()
                .statusCode(200)
                // Exposable, non-identity fields are present and correct...
                .body("metric", equalTo("PM2.5"))
                .body("value", equalTo(12.3f))
                .body("unit", equalTo("ug/m3"))
                // ...the identity lives solely in the URN, never as a flat field...
                .body("id", equalTo("urn:refrax:AirQualityReading:" + sensorId))
                .body("$", not(hasKey("sensorId")))
                // ...and every internal field is absent from the projection.
                .body("$", not(hasKey("deviceDbId")))
                .body("$", not(hasKey("userId")))
                .body("$", not(hasKey("partitionKey")))
                // Belt-and-braces: the raw serialized body mentions no internal field at all.
                .body(not(containsString("deviceDbId")))
                .body(not(containsString("userId")))
                .body(not(containsString("partitionKey")));
    }
}
