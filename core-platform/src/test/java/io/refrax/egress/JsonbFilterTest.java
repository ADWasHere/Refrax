package io.refrax.egress;

import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;
import io.refrax.schema.FieldType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonbFilterTest {

    private static final FieldDeclaration NUMBER_FIELD =
            new FieldDeclaration("value", FieldRole.PROPERTY, FieldType.NUMBER, "https://example.com/value", false);
    private static final FieldDeclaration TIMESTAMP_FIELD =
            new FieldDeclaration("observedAt", FieldRole.PROPERTY, FieldType.TIMESTAMP, "https://example.com/observedAt", false);
    private static final FieldDeclaration BOOLEAN_FIELD =
            new FieldDeclaration("active", FieldRole.PROPERTY, FieldType.BOOLEAN, "https://example.com/active", false);
    private static final FieldDeclaration STRING_FIELD =
            new FieldDeclaration("status", FieldRole.PROPERTY, FieldType.STRING, "https://example.com/status", false);

    @Test
    void numericFilterAcceptsValidComparisonOperatorsAndBindsTypedValues() {
        SqlBuilder q = new SqlBuilder("select * from reading_latest where 1=1");

        JsonbFilter.appendJsonbFilter(q, "value", NUMBER_FIELD, "gte", "12.5");

        assertTrue(q.sql().contains("::numeric >= ?3::numeric"));
        assertEquals(List.of("value", "value", new BigDecimal("12.5")), q.params());
    }

    @Test
    void numericComparisonUsesNumericCastToAvoidLexicographicOrdering() {
        SqlBuilder q = new SqlBuilder("select * from reading_latest where 1=1");

        JsonbFilter.appendJsonbFilter(q, "value", NUMBER_FIELD, "gte", "20");

        assertTrue(q.sql().contains("::numeric >= ?3::numeric"));
        assertFalse(q.sql().contains("::text >=") || q.sql().contains("->> 'value' >="));
        assertEquals(List.of("value", "value", new BigDecimal("20")), q.params());
        assertEquals(BigDecimal.class, q.params().get(2).getClass());
    }

    @Test
    void numericFilterRejectsNonNumericInputAndUnsupportedOperators() {
        IllegalArgumentException nonNumeric = assertThrows(IllegalArgumentException.class,
                () -> JsonbFilter.appendJsonbFilter(new SqlBuilder("select * from reading_latest where 1=1"),
                        "value", NUMBER_FIELD, "lt", "abc"));
        assertTrue(nonNumeric.getMessage().contains("not a number"));

        IllegalArgumentException unsupportedOp = assertThrows(IllegalArgumentException.class,
                () -> JsonbFilter.appendJsonbFilter(new SqlBuilder("select * from reading_latest where 1=1"),
                        "value", NUMBER_FIELD, "neq", "12"));
        assertTrue(unsupportedOp.getMessage().contains("not allowed"));
    }

    @Test
    void timestampFilterAcceptsIso8601ValuesAndRejectsBadInput() {
        SqlBuilder q = new SqlBuilder("select * from reading_latest where 1=1");
        OffsetDateTime expected = OffsetDateTime.parse("2026-07-02T10:00:00Z");

        JsonbFilter.appendJsonbFilter(q, "observedAt", TIMESTAMP_FIELD, "lt", "2026-07-02T10:00:00Z");

        assertTrue(q.sql().contains("::timestamptz < ?3::timestamptz"));
        assertEquals(List.of("observedAt", "observedAt", expected), q.params());

        IllegalArgumentException badTs = assertThrows(IllegalArgumentException.class,
                () -> JsonbFilter.appendJsonbFilter(new SqlBuilder("select * from reading_latest where 1=1"),
                        "observedAt", TIMESTAMP_FIELD, "gte", "not-a-date"));
        assertTrue(badTs.getMessage().contains("valid ISO-8601 timestamp"));
    }

    @Test
    void booleanFilterAllowsEqualityAndRejectsComparisons() {
        SqlBuilder q = new SqlBuilder("select * from reading_latest where 1=1");

        JsonbFilter.appendJsonbFilter(q, "active", BOOLEAN_FIELD, "eq", "true");

        assertTrue(q.sql().contains("::boolean = ?2::boolean"));
        assertEquals(List.of("active", true), q.params());

        IllegalArgumentException invalidOp = assertThrows(IllegalArgumentException.class,
                () -> JsonbFilter.appendJsonbFilter(new SqlBuilder("select * from reading_latest where 1=1"),
                        "active", BOOLEAN_FIELD, "gt", "true"));
        assertTrue(invalidOp.getMessage().contains("Only equality is supported"));
    }

    @Test
    void stringFilterAllowsExactMatchAndRejectsComparisonOperators() {
        SqlBuilder q = new SqlBuilder("select * from reading_latest where 1=1");

        JsonbFilter.appendJsonbFilter(q, "status", STRING_FIELD, "eq", "ok");

        assertTrue(q.sql().contains("exposed_json ->> ?1 = ?2"));
        assertEquals(List.of("status", "ok"), q.params());

        IllegalArgumentException invalidOp = assertThrows(IllegalArgumentException.class,
                () -> JsonbFilter.appendJsonbFilter(new SqlBuilder("select * from reading_latest where 1=1"),
                        "status", STRING_FIELD, "lt", "ok"));
        assertTrue(invalidOp.getMessage().contains("Only equality is supported"));
    }
}
