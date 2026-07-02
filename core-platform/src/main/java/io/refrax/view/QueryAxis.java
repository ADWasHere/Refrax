package io.refrax.view;

import java.util.Objects;

/**
 * A declared query axis: the field it filters on and how it matches. Axes are the only way
 * to query a view — filtering on an undeclared axis is rejected, so query hits cannot be
 * used to probe fields the view does not expose.
 *
 * @param field the field the axis queries (a payload field, an identity component, or
 *              {@code validTime})
 * @param match how the axis matches values
 */
public record QueryAxis(String field, MatchType match) {

    public QueryAxis {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(match, "match");
    }
}
