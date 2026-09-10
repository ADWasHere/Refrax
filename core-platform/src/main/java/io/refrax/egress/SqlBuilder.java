package io.refrax.egress;

import java.util.ArrayList;
import java.util.List;

/**
 * A parameterised native-SQL statement under construction, executed via the Hibernate Reactive
 * session (see {@link ViewQueryService}) so it runs on the connection {@code TenantAwarePanache}
 * pinned to the tenant's schema.
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

    /** Binds a value and appends its positional placeholder ({@code ?n}, Hibernate's ordinal style). */
    SqlBuilder bind(Object value) {
        params.add(value);
        sql.append('?').append(params.size());
        return this;
    }

    String sql() {
        return sql.toString();
    }

    List<Object> params() {
        return List.copyOf(params);
    }
}
