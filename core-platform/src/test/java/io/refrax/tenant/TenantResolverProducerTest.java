package io.refrax.tenant;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TenantResolverProducerTest {

    @Test
    void whenStrategyHeader_thenHeaderResolverProduced() {
        TokenClaimTenantResolver fakeToken = new TokenClaimTenantResolver(null, "org_id", true) {
            @Override
            public String resolveTenantId(ContainerRequestContext requestContext) {
                return "token";
            }
        };
        HeaderTenantResolver header = new HeaderTenantResolver();

        TenantResolverProducer producer = new TenantResolverProducer(fakeToken, header, "header");
        TenantResolver resolver = producer.produce();

        assertInstanceOf(HeaderTenantResolver.class, resolver);
    }

    @Test
    void whenStrategyClaimOrMissing_thenTokenResolverProduced() {
        TokenClaimTenantResolver fakeToken = new TokenClaimTenantResolver(null, "org_id", true) {
            @Override
            public String resolveTenantId(ContainerRequestContext requestContext) {
                return "token";
            }
        };
        HeaderTenantResolver header = new HeaderTenantResolver();

        TenantResolverProducer producer = new TenantResolverProducer(fakeToken, header, null);
        TenantResolver resolver = producer.produce();

        assertInstanceOf(TokenClaimTenantResolver.class, resolver);

        // also test explicit "claim"
        producer = new TenantResolverProducer(fakeToken, header, "claim");
        resolver = producer.produce();
        assertInstanceOf(TokenClaimTenantResolver.class, resolver);
    }
}
