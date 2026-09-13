package io.refrax.readmodel;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tenant read-model lag as a gauge. A gauge needs a value to keep reading from, so one
 * {@link AtomicLong} per tenant is registered once (the first time that tenant is seen) and
 * updated in place afterwards — Micrometer reads it live on every {@code /q/metrics} scrape,
 * so there is nothing to "publish" beyond keeping the value current.
 */
@ApplicationScoped
public class ReadModelMetrics {

    @Inject
    MeterRegistry registry;

    private final Map<String, AtomicLong> lagByTenant = new ConcurrentHashMap<>();

    public void recordLag(String tenant, long lag) {
        lagByTenant.computeIfAbsent(tenant, t -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("refrax.readmodel.lag", value, AtomicLong::get)
                    .description("Events behind the log for this tenant's read model")
                    .tag("tenant", t)
                    .register(registry);
            return value;
        }).set(lag);
    }
}
