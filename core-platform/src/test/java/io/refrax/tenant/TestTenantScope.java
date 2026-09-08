package io.refrax.tenant;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.function.Supplier;

/**
 * Test-only bridge that gives a direct (non-HTTP) call its own isolated tenant/request scope,
 * mirroring how {@code TenantJobRunner} does it in production. Needed because {@link TenantContext}
 * is request-scoped and test helpers like {@code await(...)} run bare on a Vert.x context, with no
 * HTTP request (and therefore no {@code TenantResolverFilter}) behind them to populate it.
 */
@ApplicationScoped
public class TestTenantScope {

    @Inject
    TenantContext tenantContext;

    @ActivateRequestContext
    public <T> Uni<T> run(String tenant, Supplier<Uni<T>> action) {
        tenantContext.setTenantId(tenant);
        return action.get();
    }
}
