package io.refrax.schema;

/**
 * Thrown when a declared schema is invalid or unreadable. Raised at load time so an
 * invalid declaration is rejected before the application serves any request.
 */
public class SchemaValidationException extends RuntimeException {

    public SchemaValidationException(String message) {
        super(message);
    }
}
