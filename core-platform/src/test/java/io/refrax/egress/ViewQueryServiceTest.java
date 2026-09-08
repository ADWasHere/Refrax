package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
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

@QuarkusTest
class ViewQueryServiceTest extends EgressTestSupport {

    @Test
    void gateProofInternalFieldsNeverLeakIntoProjection() {
        String sensorId = "sensor-" + UUID.randomUUID();
        String pollutedEvent = """
                {
                  "eventType": "AirQualityReading",
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

        given().contentType("application/json")
                .header("X-Tenant-ID", "public")
                .body(pollutedEvent)
                .when().post("/v1/events").then().statusCode(202);
        await(() -> consumer.catchUp());

        given().header("X-Tenant-ID", "public")
                .queryParam("sensor", sensorId)
                .when().get("/v1/views/air-quality-full/latest")
                .then().statusCode(200)
                .body("metric", org.hamcrest.Matchers.equalTo("PM2.5"))
                .body("value", org.hamcrest.Matchers.equalTo(12.3f))
                .body("unit", org.hamcrest.Matchers.equalTo("ug/m3"))
                .body("id", org.hamcrest.Matchers.equalTo("urn:refrax:AirQualityReading:" + sensorId))
                .body("$", not(org.hamcrest.Matchers.hasKey("sensorId")))
                .body("$", not(org.hamcrest.Matchers.hasKey("deviceDbId")))
                .body("$", not(org.hamcrest.Matchers.hasKey("userId")))
                .body("$", not(org.hamcrest.Matchers.hasKey("partitionKey")))
                .body(not(containsString("deviceDbId")))
                .body(not(containsString("userId")))
                .body(not(containsString("partitionKey")));
    }

    @Test
    void seriesReturnsTheValidTimeWindowForASensor() {
        String sensor = "sensor-" + UUID.randomUUID();
        ingestAt(sensor, "2026-07-02T10:00:00Z", 1.0);
        ingestAt(sensor, "2026-07-02T10:05:00Z", 2.0);
        ingestAt(sensor, "2026-07-02T10:10:00Z", 3.0);
        await(() -> consumer.catchUp());

        JsonPath series = given().header("X-Tenant-ID", "public")
                .queryParam("sensor", sensor)
                .queryParam("from", "2026-07-02T10:04:00Z")
                .queryParam("to", "2026-07-02T10:11:00Z")
                .when().get("/v1/views/air-quality-full/series")
                .then().statusCode(200)
                .extract().jsonPath();

        List<Float> values = series.getList("event.value", Float.class);
        assertEquals(List.of(2.0f, 3.0f), values);
    }

    @Test
    void streamReturnsOrderedGatedSliceAfterCursor() {
        long seqA = ingest("sensor-" + UUID.randomUUID(), 11.1);
        long seqB = ingest("sensor-" + UUID.randomUUID(), 22.2);

        JsonPath slice = given().header("X-Tenant-ID", "public")
                .when().get("/v1/views/air-quality-full/stream?after=" + (seqA - 1))
                .then().statusCode(200)
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

        JsonPath slice = given().header("X-Tenant-ID", "public")
                .when().get("/v1/views/air-quality-value-only/stream?after=" + (seq - 1))
                .then().statusCode(200)
                .extract().jsonPath();

        assertNull(slice.get("find { it.seq == " + seq + " }.event.metric"));
        assertEquals(44.4f, slice.getFloat("find { it.seq == " + seq + " }.event.value"));
    }

    @Test
    void cursorExcludesEventsAtOrBeforeIt() {
        long seqA = ingest("sensor-" + UUID.randomUUID(), 33.3);

        JsonPath slice = given().header("X-Tenant-ID", "public")
                .when().get("/v1/views/air-quality-full/stream?after=" + seqA)
                .then().statusCode(200)
                .extract().jsonPath();

        assertNull(slice.get("find { it.seq == " + seqA + " }"));
    }

    @Test
    void streamUnknownViewIsRejected() {
        given().header("X-Tenant-ID", "public")
                .when().get("/v1/views/does-not-exist/stream?after=0")
                .then().statusCode(400);
    }
}
