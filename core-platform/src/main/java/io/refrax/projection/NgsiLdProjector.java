package io.refrax.projection;

import io.refrax.gate.ExposableEntity;
import io.refrax.schema.FieldRole;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Renders an entity as an NGSI-LD entity. Identity is carried structurally in {@code id};
 * each exposable field becomes a typed node — a {@code Property} with a {@code value}, or a
 * {@code Relationship} with an {@code object}.
 *
 * <p>A field is exposed under its own name unless that name collides with an NGSI-LD
 * structural key (e.g. {@code value}). Collisions are resolved here — where the knowledge of
 * NGSI-LD's reserved keys belongs — by deriving a safe term from the field's vocabulary URI,
 * which is already the field's true, collision-free name. Nothing is invented and the schema
 * needs no NGSI-LD-specific hints. The {@code @context} maps each chosen term to its
 * vocabulary URI, as an array alongside the NGSI-LD core context, never hand-authored.
 */
@ApplicationScoped
public class NgsiLdProjector implements Projector {

    private static final String CORE_CONTEXT =
            "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld";

    /** NGSI-LD structural keys a domain field name must not collide with. */
    private static final Set<String> RESERVED = Set.of(
            "id", "type", "value", "object", "observedAt", "unitCode",
            "datasetId", "instanceId", "createdAt", "modifiedAt", "@context");

    @Override
    public String format() {
        return "ngsi-ld";
    }

    @Override
    public JsonObject project(ExposableEntity entity) {
        JsonObject out = new JsonObject();
        out.put("id", entity.id());
        out.put("type", entity.type());

        JsonObject terms = new JsonObject();
        entity.properties().forEach((name, prop) -> {
            String term = localTerm(name, prop.vocabularyUri());

            JsonObject node = new JsonObject();
            if (prop.role() == FieldRole.RELATIONSHIP) {
                node.put("type", "Relationship");
                node.put("object", prop.value());
            } else {
                node.put("type", "Property");
                node.put("value", prop.value());
            }
            out.put(term, node);
            terms.put(term, prop.vocabularyUri());
        });

        out.put("@context", new JsonArray().add(terms).add(CORE_CONTEXT));
        return out;
    }

    /**
     * The field name, unless it collides with an NGSI-LD reserved key — then a safe term
     * derived from the vocabulary URI's last segment (the field's semantic name anyway).
     */
    private static String localTerm(String fieldName, String vocabularyUri) {
        if (!RESERVED.contains(fieldName)) {
            return fieldName;
        }
        int cut = Math.max(vocabularyUri.lastIndexOf('/'), vocabularyUri.lastIndexOf('#'));
        String derived = (cut >= 0 && cut < vocabularyUri.length() - 1)
                ? vocabularyUri.substring(cut + 1)
                : "";
        return derived.isBlank() ? fieldName : derived;
    }
}
