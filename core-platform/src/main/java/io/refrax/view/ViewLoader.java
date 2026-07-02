package io.refrax.view;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads a {@link View} from a JSON declaration on the classpath. Parsing is lenient about
 * unknown attributes and case for match kinds ({@code "exact"} maps to {@link MatchType#EXACT});
 * structural correctness against the schema is the {@link ViewValidator}'s job.
 */
public final class ViewLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ViewLoader() {
    }

    public static View load(String location) {
        Objects.requireNonNull(location);
        if (location.isBlank()) {
            throw new ViewValidationException("View location cannot be blank");
        }

        ViewDocument doc = read(location);
        return toView(doc, location);
    }

    private static ViewDocument read(String location) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(location)) {
            if (in == null) {
                throw new ViewValidationException("View resource not found on classpath: " + location);
            }
            return MAPPER.readValue(in, ViewDocument.class);
        } catch (IOException e) {
            throw new ViewValidationException("Failed to parse view '" + location + "': " + e.getMessage());
        }
    }

    private static View toView(ViewDocument doc, String location) {
        if (doc.exposes() == null) {
            throw new ViewValidationException("View '" + location + "' declares no exposes list");
        }
        Map<String, QueryAxis> axes = new LinkedHashMap<>();
        if (doc.queryable() != null) {
            for (Map.Entry<String, ViewDocument.AxisSpec> entry : doc.queryable().entrySet()) {
                ViewDocument.AxisSpec spec = entry.getValue();
                if (spec.field() == null || spec.match() == null) {
                    throw new ViewValidationException(
                            "Axis '" + entry.getKey() + "' in view '" + location
                                    + "' must declare both field and match");
                }
                axes.put(entry.getKey(), new QueryAxis(spec.field(), spec.match()));
            }
        }
        return new View(doc.name(), doc.eventType(), doc.exposes(), axes);
    }
}
