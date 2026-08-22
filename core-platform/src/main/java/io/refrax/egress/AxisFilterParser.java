package io.refrax.egress;

import io.refrax.schema.FieldDeclaration;
import io.refrax.view.MatchType;
import io.refrax.view.QueryAxis;
import io.refrax.view.ViewBinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedMap;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the WHERE clause fragments for declared query axes.
 *
 * <p>The parser understands direct equality filters and typed comparison suffixes such as
 * {@code .gt} (greater than), {@code .gte} (greater than or equal), {@code .lt} (less than) and {@code .lte} (less than or equal).
 */
@ApplicationScoped
public class AxisFilterParser {

    /**
     * Appends all axis-based WHERE predicates for the current request.
     *
     * @param binding the validated view/schema binding
     * @param query the raw request query parameters
     * @param reserved parameter names that must be skipped
     * @param q the SQL builder under construction
     */
    public void appendAxisFilters(ViewBinding binding,
                                 MultivaluedMap<String, String> query,
                                 Set<String> reserved,
                                 SqlBuilder q) {
        for (Map.Entry<String, List<String>> entry : query.entrySet()) {
            String key = entry.getKey();
            if (reserved.contains(key)) {
                continue;
            }

            String[] parsed = parseAxisKey(key);
            String baseKey = parsed[0];
            String op = parsed[1];

            QueryAxis axis = binding.axis(baseKey)
                    .orElseThrow(() -> new IllegalArgumentException("Undeclared query axis: " + baseKey));
            if (axis.match() == MatchType.RANGE) {
                throw new IllegalArgumentException(
                        "Range axis '" + baseKey + "' is not queryable directly; use from/to on /series");
            }

            String value = entry.getValue().getFirst();
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Query axis '" + baseKey + "' requires a value");
            }

            if (binding.isIdentityField(axis.field())) {
                if (binding.identityFieldNames().size() > 1) {
                    throw new IllegalArgumentException("Cannot filter by a single component of a composite identity");
                }
                if (!"eq".equals(op)) {
                    throw new IllegalArgumentException("Comparison operators are not supported for identity filters");
                }
                q.sql(" and entity_id = ").bind(binding.entityUrn(value));
            } else {
                FieldDeclaration field = binding.schema().field(axis.field())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Axis '" + baseKey + "' refers to undeclared field: " + axis.field()));
                JsonbFilter.appendJsonbFilter(q, axis.field(), field, op, value);
            }
        }
    }

    /**
     * Appends valid-time range filters from the request's {@code from} and {@code to} parameters.
     *
     * @param query the request query parameters
     * @param q the SQL builder under construction
     */
    public void appendTimeFilters(MultivaluedMap<String, String> query, SqlBuilder q) {
        String from = query.getFirst("from");
        if (from != null) {
            q.sql(" and observed_at >= ").bind(parseTime("from", from));
        }
        String to = query.getFirst("to");
        if (to != null) {
            q.sql(" and observed_at <= ").bind(parseTime("to", to));
        }
    }

    /**
     * Parses a timestamp string into an {@link OffsetDateTime}.
     *
     * @param param the originating query parameter name (for error reporting)
     * @param raw the raw timestamp value
     * @return the parsed timestamp
     * @throws IllegalArgumentException if the timestamp is not valid ISO-8601
     */
    public OffsetDateTime parseTime(String param, String raw) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Parameter '" + param + "' is not a valid ISO-8601 timestamp: " + raw);
        }
    }

    private static String[] parseAxisKey(String key) {
        if (key.endsWith(".gte")) {
            return new String[]{key.substring(0, key.length() - 4), "gte"};
        }
        if (key.endsWith(".lte")) {
            return new String[]{key.substring(0, key.length() - 4), "lte"};
        }
        if (key.endsWith(".gt")) {
            return new String[]{key.substring(0, key.length() - 3), "gt"};
        }
        if (key.endsWith(".lt")) {
            return new String[]{key.substring(0, key.length() - 3), "lt"};
        }
        if (key.endsWith(".eq")) {
            return new String[]{key.substring(0, key.length() - 3), "eq"};
        }
        return new String[]{key, "eq"};
    }
}
