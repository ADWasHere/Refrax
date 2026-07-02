package io.refrax.schema;

/**
 * The semantic role a declared field plays in the domain model.
 *
 * <p>Only exposable roles exist here. A field is internal precisely by having no
 * declaration at all — internality is the absence of a role, not a role. The set is
 * closed and compiler-checked, so a misspelt or invented role cannot be declared.
 */
public enum FieldRole {

    /** Component of the domain identity; the entity URN is built only from these. */
    IDENTITY,

    /** An observed/measured property of the entity. */
    PROPERTY,

    /** A reference to another entity. */
    RELATIONSHIP;

    /**
     * Whether a field with this role may appear in an exposed representation. Every role
     * is currently exposable; this remains the single place to say otherwise should a
     * declared-but-not-exposed role (e.g. metadata) ever be introduced.
     */
    public boolean exposable() {
        return true;
    }
}
