package io.refrax.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads an {@link EventSchema} from a JSON declaration on the classpath. Parsing
 * is lenient about unknown attributes and case for role values ({@code "identity"} maps
 * to {@link FieldRole#IDENTITY}); structural correctness is the {@link SchemaValidator}'s
 * job.
 */
public final class SchemaLoader {

    private static final String DEFAULT_URN_NAMESPACE = "urn:refrax";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SchemaLoader() {
    }

    public static EventSchema load(String location) {
        Objects.requireNonNull(location);

        if(location.isEmpty()){
            throw new IllegalArgumentException("Location cannot be empty");
        }

        SchemaDocument document = read(location);
        return toEventSchema(document, location);
    }

    private static SchemaDocument read(String location) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(location)) {
            if (in == null) {
                throw new SchemaValidationException("Schema resource not found on classpath: " + location);
            }
            return MAPPER.readValue(in, SchemaDocument.class);
        } catch (IOException e) {
            throw new SchemaValidationException("Failed to parse schema '" + location + "': " + e.getMessage());
        }
    }

    private static EventSchema toEventSchema(SchemaDocument doc, String location) {
        if (doc.exposable() == null || doc.exposable().isEmpty()) {
            throw new SchemaValidationException("Schema '" + location + "' declares no exposable fields");
        }
        String namespace = (doc.urnNamespace() == null || doc.urnNamespace().isBlank())
                ? DEFAULT_URN_NAMESPACE
                : doc.urnNamespace();

        List<FieldDeclaration> fields = new ArrayList<>();
        for (Map.Entry<String, SchemaDocument.FieldSpec> entry : doc.exposable().entrySet()) {
            SchemaDocument.FieldSpec spec = entry.getValue();
            if (spec.role() == null) {
                throw new SchemaValidationException(
                        "Field '" + entry.getKey() + "' in schema '" + location + "' declares no role");
            }
            fields.add(new FieldDeclaration(
                    entry.getKey(), spec.role(), spec.vocabularyUri(), spec.personalData()));
        }
        return new EventSchema(doc.eventType(), namespace, fields);
    }
}
