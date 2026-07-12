package io.refrax.view;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A view bound to the schema it is over, <b>validated on construction</b>: you cannot hold a
 * {@code ViewBinding} whose view does not fit its schema. The view↔schema consistency is
 * established once, here — every downstream reader takes a binding and trusts it, instead of
 * re-validating or looking the two up separately and hoping they match.
 */
public record ViewBinding(View view, EventSchema schema) {

    public ViewBinding {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(schema, "schema");
        ViewValidator.validate(view, schema);
    }

    public String eventType() {
        return schema.eventType();
    }

    public Optional<QueryAxis> axis(String axisName) {
        return view.axis(axisName);
    }

    public boolean exposesField(String fieldName) {
        return view.exposesField(fieldName);
    }

    public Set<String> identityFieldNames() {
        return schema.identityComponents().stream()
                .map(FieldDeclaration::name)
                .collect(Collectors.toSet());
    }

    public boolean isIdentityField(String fieldName) {
        return identityFieldNames().contains(fieldName);
    }

    /**
     * The entity URN for a single identity value — valid only for single-component identities.
     * Delegates to {@link EventSchema#urn} so the filter cannot drift from what the gate wrote.
     */
    public String entityUrn(String identityValue) {
        return schema.urn(List.of(identityValue));
    }
}
