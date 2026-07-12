package io.refrax.egress;

import java.util.ArrayList;
import java.util.List;

/**
 * A parameterised SQL statement under construction: its text and its bound parameters are one
 * thing, held together and numbered ($1, $2, …) automatically. Callers append fragments and
 * bind values without tracking positions by hand.
 */
final class SqlBuilder {

    private final StringBuilder sql = new StringBuilder();
    private final List<Object> params = new ArrayList<>();

    SqlBuilder(String base) {
        sql.append(base);
    }

    SqlBuilder sql(String fragment) {
        sql.append(fragment);
        return this;
    }

    /** Binds a value and appends its positional placeholder ({@code $n}). */
    SqlBuilder bind(Object value) {
        params.add(value);
        sql.append('$').append(params.size());
        return this;
    }

    String sql() {
        return sql.toString();
    }

    List<Object> params() {
        return List.copyOf(params);
    }
}
