package  com.sujon.analytics_engine.exception;

/**
 * Thrown when a requested resource does not exist.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
