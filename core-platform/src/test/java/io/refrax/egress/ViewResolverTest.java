package io.refrax.egress;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ViewResolverTest {

    @Inject
    ViewResolver resolver;

    @Test
    void resolvesKnownViewWithDefaultNativeFormat() {
        ViewResolver.Resolved resolved = resolver.resolve("air-quality-full", null);

        assertNotNull(resolved);
        assertEquals("AirQualityReading", resolved.binding().eventType());
        assertNotNull(resolved.projector());
    }

    @Test
    void rejectsUnknownView() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resolver.resolve("does-not-exist", "native"));

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void rejectsUnknownFormat() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resolver.resolve("air-quality-full", "xml"));

        assertEquals(400, ex.getResponse().getStatus());
    }
}
