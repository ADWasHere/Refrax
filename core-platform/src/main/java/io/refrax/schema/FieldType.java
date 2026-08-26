package io.refrax.schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum FieldType {
    STRING,
    NUMBER,
    BOOLEAN,
    TIMESTAMP
}
