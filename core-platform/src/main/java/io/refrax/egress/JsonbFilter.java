package io.refrax.egress;

import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JSONB filter helper that builds WHERE fragments and binds typed parameters.
 * Supports operators: eq, gt, gte, lt, lte.
 *
 * <p>Every cast keeps a space before its {@code ::type}, e.g. {@code ?4 ::numeric} rather than
 * {@code ?4::numeric}: Hibernate's ordinal-parameter parser misreads a placeholder immediately
 * followed by a cast and fails with "Ordinal parameter label was not an integer".
 */
final class JsonbFilter {
    private JsonbFilter() {}

    // Bound as parameters rather than spliced into the SQL text, so a literal '?' in a regex
    // (e.g. the "-?" in NUMERIC_PATTERN) is never mistaken for one of Hibernate's own ordinal
    // placeholders.
    private static final String NUMERIC_PATTERN = "^-?[0-9]+(\\.[0-9]+)?$";
    private static final String TIMESTAMP_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}T";

    static void appendJsonbFilter(SqlBuilder q, String fieldName, FieldDeclaration fd, String op, String value) {
        FieldType ft = fd.type();
        String sqlOp = sqlJsonbOperator(op);

        switch (ft) {
            case NUMBER: {
                if (!isComparisonOp(op)) {
                    throw new IllegalArgumentException("Operator '" + op + "' not allowed for numeric field '" + fieldName + "'");
                }
                BigDecimal num;
                try {
                    num = new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Parameter for numeric comparison is not a number: " + value);
                }
                q.sql(" and (exposed_json ->> ").bind(fieldName)
                        .sql(") ~ ").bind(NUMERIC_PATTERN)
                        .sql(" and (exposed_json ->> ").bind(fieldName)
                        .sql(")::numeric ").sql(sqlOp).sql(" ").bind(num).sql(" ::numeric");
                break;
            }

            case TIMESTAMP: {
                if (!isComparisonOp(op)) {
                    throw new IllegalArgumentException("Operator '" + op + "' not allowed for timestamp field '" + fieldName + "'");
                }
                OffsetDateTime odt;
                try {
                    odt = OffsetDateTime.parse(value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Parameter for timestamp comparison is not a valid ISO-8601 timestamp: " + value);
                }
                q.sql(" and (exposed_json ->> ").bind(fieldName)
                        .sql(") ~ ").bind(TIMESTAMP_PATTERN)
                        .sql(" and (exposed_json ->> ").bind(fieldName)
                        .sql(")::timestamptz ").sql(sqlOp).sql(" ").bind(odt).sql(" ::timestamptz");
                break;
            }

            case BOOLEAN: {
                if (!"eq".equals(op)) {
                    throw new IllegalArgumentException("Only equality is supported for boolean field '" + fieldName + "'");
                }
                Boolean b = Boolean.parseBoolean(value);
                q.sql(" and (exposed_json ->> ").bind(fieldName)
                        .sql(")::boolean = ").bind(b).sql(" ::boolean");
                break;
            }

            case STRING:
            default: {
                if (!"eq".equals(op)) {
                    throw new IllegalArgumentException("Only equality is supported for string field '" + fieldName + "'");
                }
                q.sql(" and exposed_json ->> ").bind(fieldName)
                        .sql(" = ").bind(value);
                break;
            }
        }
    }

    private static boolean isComparisonOp(String op) {
        return "gt".equals(op) || "gte".equals(op) || "lt".equals(op) || "lte".equals(op) || "eq".equals(op);
    }

    private static String sqlJsonbOperator(String op) {
        return switch (op) {
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            default -> "=";
        };
    }
}
