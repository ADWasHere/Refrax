package io.refrax.view;

import io.refrax.schema.EventSchema;
import io.refrax.schema.FieldDeclaration;
import io.refrax.schema.FieldRole;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, load-time validation of a view against its schema. A view may only shrink
 * the schema's exposable set, and may only be queried on fields it (or the identity) makes
 * available:
 *
 * <ul>
 *   <li>every exposed field is a schema property/relationship (never the identity, never a
 *       field the schema does not expose);</li>
 *   <li>every query axis points at an exposed field, an identity component, or the
 *       valid-time;</li>
 *   <li>no {@code personalData} field is used as an exact-match axis (so its value cannot be
 *       probed by equality). This is enforced now though it only bites once fields are
 *       classified as personal.</li>
 * </ul>
 *
 * The first violation throws {@link ViewValidationException}.
 */
public final class ViewValidator {

    /** The envelope valid-time, addressable as a query axis field though it is not a payload field. */
    public static final String VALID_TIME = "validTime";

    private ViewValidator() {
    }

    public static void validate(View view, EventSchema schema) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(schema, "schema");

        if (view.name() == null || view.name().isBlank()) {
            throw new ViewValidationException("View has no name");
        }
        if (!schema.eventType().equals(view.eventType())) {
            throw new ViewValidationException(
                    "View '" + view.name() + "' is over event type '" + view.eventType()
                            + "' but was validated against schema '" + schema.eventType() + "'");
        }

        for (String exposed : view.exposes()) {
            FieldDeclaration field = schema.field(exposed).orElseThrow(() -> new ViewValidationException(
                    "View '" + view.name() + "' exposes '" + exposed
                            + "', which the schema does not declare as exposable"));
            if (field.role() == FieldRole.IDENTITY) {
                throw new ViewValidationException(
                        "View '" + view.name() + "' exposes identity field '" + exposed
                                + "'; identity is structural and lives only in the URN");
            }
        }

        Set<String> addressable = new HashSet<>(view.exposes());
        schema.identityComponents().forEach(id -> addressable.add(id.name()));
        addressable.add(VALID_TIME);

        view.queryable().forEach((axisName, axis) -> {
            if (!addressable.contains(axis.field())) {
                throw new ViewValidationException(
                        "View '" + view.name() + "' axis '" + axisName + "' points at '" + axis.field()
                                + "', which the view neither exposes nor is an identity/valid-time field");
            }
            if (axis.match() == MatchType.EXACT) {
                schema.field(axis.field())
                        .filter(FieldDeclaration::personalData)
                        .ifPresent(f -> {
                            throw new ViewValidationException(
                                    "View '" + view.name() + "' axis '" + axisName + "' is an exact match on "
                                            + "personal-data field '" + axis.field() + "'");
                        });
            }
        });
    }
}
