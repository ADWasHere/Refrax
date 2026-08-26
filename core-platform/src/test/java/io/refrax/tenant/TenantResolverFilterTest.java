package io.refrax.tenant;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@QuarkusTest
class TenantResolverFilterTest {

    @Inject
    TenantResolverFilter filter;

    @Inject
    TenantContext tenantContext;

    @InjectMock
    HeaderTenantResolver tenantResolverMock;

    private ContainerRequestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = mock(ContainerRequestContext.class);
    }

    @Test
    void setsTenantContextWhenResolverReturnsTenant() {
        when(tenantResolverMock.resolveTenantId(ctx)).thenReturn("t-42");

        filter.filter(ctx);

        assertEquals("t-42", tenantContext.getTenantId());
        verify(ctx, never()).abortWith(any());
    }

    @Test
    void abortsWhenResolverReturnsNull() {
        when(tenantResolverMock.resolveTenantId(ctx)).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());

        Response response = captor.getValue();
        assertNotNull(response);
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }
}
