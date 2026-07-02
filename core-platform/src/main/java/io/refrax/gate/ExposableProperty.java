package io.refrax.gate;

import io.refrax.schema.FieldRole;

/**
 * A single exposable value together with its semantic role and the vocabulary it is bound
 * to. Carries the {@code personalData} flag so downstream projectors/views can honour it.
 * The role lets a projector choose the right shape (e.g. NGSI-LD Property vs Relationship);
 * the vocabulary URI is the collision-free, meaning-bearing name a projector can fall back
 * to when the local field name is unusable.
 *
 * @param value         the value copied from the payload
 * @param role          the field's semantic role
 * @param vocabularyUri the vocabulary binding declared for this field
 * @param personalData  whether this value is personal data
 */
public record ExposableProperty(Object value, FieldRole role, String vocabularyUri, boolean personalData) {
}
