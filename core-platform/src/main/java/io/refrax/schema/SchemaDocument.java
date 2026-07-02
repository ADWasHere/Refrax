package io.refrax.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * On-disk JSON shape of a schema declaration. Kept separate from the runtime
 * {@link EventSchema} so the file format can carry richer metadata than the gate needs.
 *
 * <p>The declaration lists <b>only</b> the {@code exposable} fields. Internal fields are
 * never declared — anything not listed here is internal by default and the gate denies
 * it. The exposable set plus its vocabulary bindings is the standard-agnostic core that
 * later projectors (NGSI-LD, OGC SensorThings, ...) convert into their own shapes.
 *
 * <p>Unknown/extra attributes (including per-field {@code type}, {@code mandatory}) are
 * tolerated so the format can grow without breaking the loader.
 *
 * @param refraxSchema the Refrax schema-format identifier/version (documentation for now)
 * @param eventType    the event type this document declares
 * @param urnNamespace optional URN namespace for entity ids; defaults to {@code urn:refrax}
 * @param exposable    field name → field spec, in declaration order
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SchemaDocument(
        String refraxSchema,
        String eventType,
        String urnNamespace,
        Map<String, FieldSpec> exposable) {

    /**
     * One exposable field. Only the attributes the gate/validator act on are modelled;
     * {@code type}, {@code mandatory}, {@code format}, ... are parsed by the format but
     * ignored at runtime for now (design-for, don't-build-yet).
     *
     * @param role          the semantic role (must be exposable)
     * @param vocabularyUri the vocabulary binding (required)
     * @param personalData  whether the field is personal data (optional, defaults false)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldSpec(
            FieldRole role,
            String vocabularyUri,
            boolean personalData) {
    }
}
