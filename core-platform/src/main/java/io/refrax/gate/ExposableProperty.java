package io.refrax.gate;

/**
 * A single exposable value together with the vocabulary it is bound to. Carries the
 * {@code personalData} flag so downstream projectors/views can honour it.
 *
 * @param value         the value copied from the payload
 * @param vocabularyUri the vocabulary binding declared for this field
 * @param personalData  whether this value is personal data
 */
public record ExposableProperty(Object value, String vocabularyUri, boolean personalData) {
}
