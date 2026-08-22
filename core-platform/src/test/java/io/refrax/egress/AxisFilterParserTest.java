package io.refrax.egress;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;
import io.refrax.schema.FieldType;
import io.refrax.view.MatchType;
import io.refrax.view.QueryAxis;
import io.refrax.view.View;
import io.refrax.view.ViewBinding;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxisFilterParserTest {

    private final AxisFilterParser parser = new AxisFilterParser();

    private ViewBinding binding() {
        EventSchema schema = new EventSchema("AirQualityReading", "urn:refrax",
                List.of(
                        new FieldDeclaration("sensor", FieldRole.IDENTITY, FieldType.STRING, "https://example.com/id", false),
                        new FieldDeclaration("value", FieldRole.PROPERTY, FieldType.NUMBER, "https://example.com/value", false),
                        new FieldDeclaration("observedAt", FieldRole.PROPERTY, FieldType.TIMESTAMP, "https://example.com/observedAt", false),
                        new FieldDeclaration("status", FieldRole.PROPERTY, FieldType.STRING, "https://example.com/status", false)
                ));

        View view = new View("air-quality-full", "AirQualityReading",
                List.of("value", "observedAt", "status"),
                Map.of(
                        "sensor", new QueryAxis("sensor", MatchType.EXACT),
                        "value", new QueryAxis("value", MatchType.EXACT),
                        "observedAt", new QueryAxis("observedAt", MatchType.EXACT),
                        "status", new QueryAxis("status", MatchType.EXACT),
                        "validTime", new QueryAxis("validTime", MatchType.RANGE)
                ));

        return new ViewBinding(view, schema);
    }

    @Test
    void appendAxisFiltersHandlesExactAndComparisonOperators() {
        ViewBinding binding = binding();
        MultivaluedHashMap<String, String> query = new MultivaluedHashMap<>();
        query.add("sensor", "sensor-42");
        query.add("value.gte", "10.5");
        query.add("observedAt.lt", "2026-07-02T10:00:00Z");
        query.add("format", "ngsi-ld");

        SqlBuilder q = new SqlBuilder("select * from reading_latest where 1=1");
        parser.appendAxisFilters(binding, query, Set.of("format"), q);

        String sql = q.sql();
        assertTrue(sql.contains("entity_id"));
        assertTrue(sql.contains("::numeric >="));
        assertTrue(sql.contains("::timestamptz <"));
        assertEquals(List.of("value", "value", new java.math.BigDecimal("10.5"), "observedAt", "observedAt", OffsetDateTime.parse("2026-07-02T10:00:00Z"), "urn:refrax:AirQualityReading:sensor-42"), q.params());
    }

    @Test
    void appendAxisFiltersRejectsUndeclaredAxesBlankValuesAndRangeQueries() {
        ViewBinding binding = binding();

        IllegalArgumentException undeclared = assertThrows(IllegalArgumentException.class,
                () -> parser.appendAxisFilters(binding,
                        queryMap(Map.of("metric", "pm25")),
                        Set.of(),
                        new SqlBuilder("select * from reading_latest where 1=1")));
        assertTrue(undeclared.getMessage().contains("Undeclared query axis"));

        IllegalArgumentException blankValue = assertThrows(IllegalArgumentException.class,
                () -> parser.appendAxisFilters(binding,
                        queryMap(Map.of("status", " ")),
                        Set.of(),
                        new SqlBuilder("select * from reading_latest where 1=1")));
        assertTrue(blankValue.getMessage().contains("requires a value"));

        IllegalArgumentException rangeAxis = assertThrows(IllegalArgumentException.class,
                () -> parser.appendAxisFilters(binding,
                        queryMap(Map.of("validTime", "2026-07-02T10:00:00Z")),
                        Set.of(),
                        new SqlBuilder("select * from reading_latest where 1=1")));
        assertTrue(rangeAxis.getMessage().contains("Range axis 'validTime'"));
    }

    @Test
    void appendTimeFiltersAcceptsValidTimesAndRejectsInvalidOnes() {
        MultivaluedHashMap<String, String> query = new MultivaluedHashMap<>();
        query.add("from", "2026-07-02T09:00:00Z");
        query.add("to", "2026-07-02T11:00:00Z");

        SqlBuilder q = new SqlBuilder("select * from reading_series where 1=1");
        parser.appendTimeFilters(query, q);

        assertEquals(List.of(OffsetDateTime.parse("2026-07-02T09:00:00Z"), OffsetDateTime.parse("2026-07-02T11:00:00Z")), q.params());

        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> parser.parseTime("from", "not-a-time"));
        assertTrue(invalid.getMessage().contains("not a valid ISO-8601 timestamp"));
    }

    @Test
    void identityFiltersRejectCompositeIdentityAndComparisonOperators() {
        EventSchema compositeSchema = new EventSchema("AirQualityReading", "urn:refrax",
                List.of(
                        new FieldDeclaration("sensor", FieldRole.IDENTITY, FieldType.STRING, "https://example.com/sensor", false),
                        new FieldDeclaration("tenant", FieldRole.IDENTITY, FieldType.STRING, "https://example.com/tenant", false)
                ));
        View compositeView = new View("air-quality-full", "AirQualityReading",
                List.of(),
                Map.of(
                        "sensor", new QueryAxis("sensor", MatchType.EXACT),
                        "tenant", new QueryAxis("tenant", MatchType.EXACT)
                ));
        ViewBinding binding = new ViewBinding(compositeView, compositeSchema);

        IllegalArgumentException composite = assertThrows(IllegalArgumentException.class,
                () -> parser.appendAxisFilters(binding,
                        queryMap(Map.of("sensor", "sensor-42")),
                        Set.of(),
                        new SqlBuilder("select * from reading_latest where 1=1")));
        assertTrue(composite.getMessage().contains("Cannot filter by a single component of a composite identity"));

        IllegalArgumentException comparison = assertThrows(IllegalArgumentException.class,
                () -> parser.appendAxisFilters(binding(),
                        queryMap(Map.of("sensor.gt", "sensor-42")),
                        Set.of(),
                        new SqlBuilder("select * from reading_latest where 1=1")));
        assertTrue(comparison.getMessage().contains("Comparison operators are not supported"));
    }

    private static MultivaluedHashMap<String, String> queryMap(Map<String, String> values) {
        MultivaluedHashMap<String, String> query = new MultivaluedHashMap<>();
        values.forEach(query::add);
        return query;
    }
}
