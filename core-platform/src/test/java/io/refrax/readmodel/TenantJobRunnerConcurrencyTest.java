package io.refrax.readmodel;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.VertxContextSupport;
import io.refrax.tenant.TenantContext;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TenantJobRunner} relies on {@code @ActivateRequestContext} to give each tenant its own
 * {@link TenantContext}. Arc's request-context activation is bound to the current Vert.x
 * (duplicated) context, not to the individual call — so running several activations on ONE shared
 * context corrupts the shared storage, whether they overlap in time ({@code .merge(n)}) or not
 * ({@code .concatenate()}; the first job's teardown is not guaranteed to complete on that shared
 * context before the next one activates). This is why {@link ReadModelConsumer#runIsolated} gives
 * each tenant a brand-new duplicated context via {@link VertxContext#createNewDuplicatedContext()}
 * instead of just chaining activations on the context it was called from.
 */
@QuarkusTest
class TenantJobRunnerConcurrencyTest {

    @Inject
    Probe probe;

    private static <T> T await(Supplier<Uni<T>> action) {
        try {
            return VertxContextSupport.subscribeAndAwait(action::get);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static List<String> tenants(int count) {
        return IntStream.range(0, count).mapToObj(i -> "tenant_" + i).toList();
    }

    /** Runs one probe call on its own fresh context — the pattern {@code runIsolated} uses. */
    private static Uni<String> onFreshContext(Probe probe, String tenant) {
        Context freshContext = VertxContext.createNewDuplicatedContext();
        return Uni.createFrom().emitter(emitter ->
                freshContext.runOnContext(ignored -> probe.setDelayAndReadBack(tenant)
                        .subscribe().with(emitter::complete, emitter::fail)));
    }

    @Test
    void concurrentMergeOnASharedContextCorruptsState() {
        List<String> tenants = tenants(20);

        // Do NOT copy this pattern for real work — it is here only to pin down the hazard above.
        RuntimeException ex = assertThrows(RuntimeException.class, () -> await(() ->
                Multi.createFrom().iterable(tenants)
                        .onItem().transformToUni(probe::setDelayAndReadBack).merge(10)
                        .collect().asList()));

        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void sequentialConcatenateOnASharedContextAlsoCorruptsState() {
        List<String> tenants = tenants(20);

        // Even with no overlap in time, chaining activations on ONE shared context is unsafe:
        // nothing guarantees the previous job's teardown finished before the next one activates.
        RuntimeException ex = assertThrows(RuntimeException.class, () -> await(() ->
                Multi.createFrom().iterable(tenants)
                        .onItem().transformToUni(probe::setDelayAndReadBack).concatenate()
                        .collect().asList()));

        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void perTenantFreshContextStaysIsolatedEvenConcurrently() {
        List<String> tenants = tenants(20);

        List<String> echoedBack = await(() -> Multi.createFrom().iterable(tenants)
                .onItem().transformToUni(t -> onFreshContext(probe, t)).merge(10)
                .collect().asList());

        // Every job must observe exactly the tenant id IT set — never one from another job.
        assertEquals(tenants.stream().sorted().toList(), echoedBack.stream().sorted().toList());
    }

    @ApplicationScoped
    public static class Probe {

        @Inject
        TenantContext tenantContext;

        @ActivateRequestContext
        public Uni<String> setDelayAndReadBack(String tenant) {
            return Uni.createFrom().item(tenant)
                    .invoke(t -> tenantContext.setTenantId(t))
                    .onItem().delayIt().by(Duration.ofMillis(ThreadLocalRandom.current().nextInt(5, 30)))
                    .map(t -> tenantContext.getTenantId());
        }
    }
}
