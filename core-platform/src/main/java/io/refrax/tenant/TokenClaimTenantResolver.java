package io.refrax.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Resolves tenant id from claims in the already-verified security identity (OIDC).
 * The claim path is configurable (dot-separated for nested maps). This resolver
 * trusts the token provided by Quarkus OIDC and does NOT read tenant from the
 * HTTP payload.
 */
@ApplicationScoped
public class TokenClaimTenantResolver implements TenantResolver {

    private final SecurityIdentity identity;
    private final String claimPath;

    @Inject
    public TokenClaimTenantResolver(SecurityIdentity identity,
                                    @ConfigProperty(name = "refrax.tenant.claim-path", defaultValue = "org_id") String claimPath) {
        this.identity = identity;
        this.claimPath = claimPath;
    }

    // Constructor for tests
    public TokenClaimTenantResolver(SecurityIdentity identity, String claimPath, boolean _testOnly) {
        this.identity = identity;
        this.claimPath = claimPath;
    }

    @Override
    public String resolveTenantId(ContainerRequestContext requestContext) {
        if (identity == null) return null;
        return ClaimPathResolver.resolveFromAttributes(identity.getAttributes(), claimPath);
    }
}
