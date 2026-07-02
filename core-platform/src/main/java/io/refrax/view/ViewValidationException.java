package io.refrax.view;

/**
 * Thrown when a declared view is invalid or unreadable. Raised at load time so an invalid
 * view is rejected before the application serves any request.
 */
public class ViewValidationException extends RuntimeException {

    public ViewValidationException(String message) {
        super(message);
    }
}
