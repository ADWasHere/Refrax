package io.refrax.view;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.refrax.shared.FileReaderHelper;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads a {@link View} from a JSON declaration on the classpath. Parsing is lenient about
 * unknown attributes and case for match kinds ({@code "exact"} maps to {@link MatchType#EXACT});
 * structural correctness against the schema is the {@link ViewValidator}'s job.
 */
public final class ViewLoader {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

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
        try (InputStream in = FileReaderHelper.openStream(location)) {
            return MAPPER.readValue(in, ViewDocument.class);
        } catch (IOException e) {
            throw new ViewValidationException("Failed to read view '" + location + "': " + e.getMessage());
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
