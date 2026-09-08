package io.refrax.readmodel;

import io.refrax.tenant.TenantContext;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import static io.quarkus.arc.ComponentsProvider.LOG;

/**
 * Runs a read-model catch-up job for a specific tenant schema. This is useful for rebuilding
 * read models after a schema has been created or when a tenant's data needs to be reprocessed.
 * Gives an isolated request context for the duration of the job, so that tenant-specific beans can be used.
 */
@ApplicationScoped
public class TenantJobRunner {
    @Inject
    TenantContext tenantContext;

    @Inject
    ReadModelConsumer readModelConsumer;

    @ActivateRequestContext
    public Uni<Void> runForTenant(String schema) {
        return Uni.createFrom().item(schema)
                .invoke(s -> tenantContext.setTenantId(s))
                .chain(s -> readModelConsumer.catchUp())
                .onFailure().invoke(ex -> LOG.errorf(ex, "Catch-up failed for tenant %s", schema))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }
}
