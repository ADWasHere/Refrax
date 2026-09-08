package io.refrax.tenant;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.refrax.tenant.exceptions.TenantNotFoundException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Confines every Panache transaction/session to the current request's tenant schema.
 * <br/><br/>
 * Unfortunately, there is no reactive extension for Hibernate Reactive that allows us to set the schema on a per-transaction basis, so we have to do it manually.
 */
@ApplicationScoped
public class TenantAwarePanache {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    @Inject
    TenantContext tenantContext;

    public <T> Uni<T> withTransaction(Supplier<Uni<T>> work) {
        return Panache.withTransaction(() -> pinSchema().flatMap(v -> work.get()));
    }

    public <T> Uni<T> withSession(Supplier<Uni<T>> work) {
        return Panache.withSession(() -> pinSchema().flatMap(v -> work.get()));
    }

    private Uni<Void> pinSchema() {
        String schema = tenantContext.getTenantId();
        requireSafeSchema(schema);

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery(
                                        "SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname = :schema)",
                                        Boolean.class
                                )
                                .setParameter("schema", schema)
                                .getSingleResult()
                                .flatMap(exists -> {
                                    if (!Boolean.TRUE.equals(exists)) {
                                        return Uni.createFrom().failure(new TenantNotFoundException(schema));
                                    }
                                    return session.createNativeMutationQuery("SET LOCAL search_path TO \"" + schema + "\"")
                                            .executeUpdate()
                                            .replaceWithVoid();
                                })
                );
    }

    /** Package-visible so the identifier check can be unit-tested without a live database. */
    static void requireSafeSchema(String schema) {
        if (schema == null || !SAFE_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Tenant id is not a valid schema identifier: " + schema);
        }
    }
}
