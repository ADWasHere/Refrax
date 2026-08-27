package io.refrax.tenant;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class HeaderTenantResolverTest {

    private static ContainerRequestContext requestWithHeader(String headerValue) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getHeaderString".equals(method.getName()) && args != null && args.length == 1) {
                    return headerValue;
                }
                // default for other methods
                return null;
            }
        };
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class[]{ContainerRequestContext.class},
                handler);
    }

    @Test
    void returnsHeaderValueWhenPresent() {
        HeaderTenantResolver resolver = new HeaderTenantResolver();
        ContainerRequestContext ctx = requestWithHeader("my-tenant");
        String tid = resolver.resolveTenantId(ctx);
        assertEquals("my-tenant", tid);
    }

    @Test
    void returnsNullWhenHeaderMissing() {
        HeaderTenantResolver resolver = new HeaderTenantResolver();
        ContainerRequestContext ctx = requestWithHeader(null);
        String tid = resolver.resolveTenantId(ctx);
        assertNull(tid);
    }
}
