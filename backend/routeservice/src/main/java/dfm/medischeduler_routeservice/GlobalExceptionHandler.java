package dfm.medischeduler_routeservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Global exception handler for all REST controllers in the Route Service.
 *
 * Catches common exceptions and returns appropriate HTTP error responses
 * so that clients receive meaningful error messages instead of raw stack traces.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Handles generic uncaught exceptions. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }

    /** Handles JSON serialization/deserialization errors. */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJsonProcessingException(JsonProcessingException e) {
        return ResponseEntity.badRequest().body("JSON processing error: " + e.getMessage());
    }

    /** Handles bean validation failures on @Valid-annotated request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body("Validation error: " + e.getMessage());
    }
}
