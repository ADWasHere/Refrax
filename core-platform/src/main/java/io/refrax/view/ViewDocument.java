package io.refrax.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * On-disk JSON shape of a view declaration. Kept separate from the runtime {@link View} so
 * the file format can grow independently. Unknown attributes are tolerated.
 *
 * @param name      the view name
 * @param eventType the event type the view is over
 * @param exposes   exposed field names
 * @param queryable axis name → axis spec
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewDocument(
        String name,
        String eventType,
        List<String> exposes,
        Map<String, AxisSpec> queryable) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AxisSpec(String field, MatchType match) {
    }
}
