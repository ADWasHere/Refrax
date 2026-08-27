package io.refrax.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Produces the TenantResolver implementation based on operator configuration.
 * Configure via:
 *   refrax.tenant.strategy=claim|header
 *   refrax.tenant.claim-path=org_id
 */
@ApplicationScoped
public class TenantResolverProducer {

    @Inject
    TokenClaimTenantResolver tokenResolver;

    @Inject
    HeaderTenantResolver headerResolver;

    @ConfigProperty(name = "refrax.tenant.strategy", defaultValue = "claim")
    String strategy;

    // Package-private constructor for unit tests to inject dependencies and strategy
    TenantResolverProducer(TokenClaimTenantResolver tokenResolver, HeaderTenantResolver headerResolver, String strategy) {
        this.tokenResolver = tokenResolver;
        this.headerResolver = headerResolver;
        this.strategy = strategy;
    }

    public TenantResolverProducer() {
    }

    @Produces
    public TenantResolver produce() {
        if (strategy != null && strategy.equalsIgnoreCase("header")) {
            return headerResolver;
        }
        return tokenResolver;
    }
}
