package io.refrax.tenant;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class TenantResolverFilterTest {

    @Test
    void setsTenantContextWhenResolverReturnsTenant() {
        TenantResolverFilter filter = new TenantResolverFilter();
        filter.tenantResolver = requestContext -> "t-42";
        filter.tenantContext = new TenantContext();

        // requestContext isn't used by the resolver in this case; pass a simple proxy
        ContainerRequestContext ctx = createSimpleCtxProxy();

        filter.filter(ctx);

        assertEquals("t-42", filter.tenantContext.getTenantId());
    }

    @Test
    void abortsWhenResolverReturnsNull() {
        TenantResolverFilter filter = new TenantResolverFilter();
        filter.tenantResolver = requestContext -> null;
        filter.tenantContext = new TenantContext();

        // create proxy that captures abortWith argument
        final Response[] captured = new Response[1];
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("abortWith".equals(method.getName()) && args != null && args.length == 1) {
                    captured[0] = (Response) args[0];
                    return null;
                }
                return null;
            }
        };
        ContainerRequestContext ctx = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class[]{ContainerRequestContext.class},
                handler);

        filter.filter(ctx);

        assertNotNull(captured[0]);
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), captured[0].getStatus());
    }

    private static ContainerRequestContext createSimpleCtxProxy() {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return null;
            }
        };
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class[]{ContainerRequestContext.class},
                handler);
    }
}
