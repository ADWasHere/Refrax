package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import io.refrax.readmodel.ReadModelConsumer;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The view-scoped stream returns the ordered log slice after a cursor, with no identity
 * assumed — but through a view, so it cannot be used to bypass the gate/view layer. Each
 * event is narrowed and gated, so internal fields never leak. Robust against the shared test
 * database by keying on the seq returned at ingest time.
 */
@QuarkusTest
class ViewStreamTest {

    @Inject
    ReadModelConsumer consumer;

    private long ingest(String sensorId, double value) {
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
        long seq = given().contentType("application/json").body(event)
                .when().post("/v1/events").then().statusCode(202)
                .extract().jsonPath().getLong("seq");
        consumer.catchUp().await().indefinitely();
        return seq;
    }

    @Test
    void streamReturnsOrderedGatedSliceAfterCursor() {
        long seqA = ingest("sensor-" + UUID.randomUUID(), 11.1);
        long seqB = ingest("sensor-" + UUID.randomUUID(), 22.2);

        JsonPath slice = given()
                .when()
                .get("/v1/views/air-quality-full/stream?after=" + (seqA - 1))
                .then()
                .statusCode(200)
                // No internal field appears anywhere in the gated slice.
                .body(not(containsString("deviceDbId")))
                .body(not(containsString("userId")))
                .body(not(containsString("partitionKey")))
                .extract().jsonPath();

        List<Long> seqs = slice.getList("seq", Long.class);
        for (int i = 1; i < seqs.size(); i++) {
            assertTrue(seqs.get(i) > seqs.get(i - 1), "slice must be ordered by seq");
        }
        assertTrue(seqs.contains(seqA));
        assertTrue(seqs.contains(seqB));
        assertEquals(11.1f, slice.getFloat("find { it.seq == " + seqA + " }.event.value"));
        assertNotNull(slice.get("find { it.seq == " + seqA + " }.event.id"));
    }

    @Test
    void streamNarrowsToTheView() {
        long seq = ingest("sensor-" + UUID.randomUUID(), 44.4);

        // value-only view hides metric — the stream respects the same narrowing as /latest.
        JsonPath slice = given()
                .when()
                .get("/v1/views/air-quality-value-only/stream?after=" + (seq - 1))
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertNull(slice.get("find { it.seq == " + seq + " }.event.metric"));
        assertEquals(44.4f, slice.getFloat("find { it.seq == " + seq + " }.event.value"));
    }

    @Test
    void cursorExcludesEventsAtOrBeforeIt() {
        long seqA = ingest("sensor-" + UUID.randomUUID(), 33.3);

        JsonPath slice = given()
                .when()
                .get("/v1/views/air-quality-full/stream?after=" + seqA)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertNull(slice.get("find { it.seq == " + seqA + " }"));
    }

    @Test
    void unknownViewIsRejected() {
        given()
                .when()
                .get("/v1/views/does-not-exist/stream?after=0")
                .then()
                .statusCode(400);
    }
}
