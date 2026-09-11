package com.railpredictor.exception;

import com.railpredictor.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every exception the API can throw to the one consistent {@link ErrorResponse} envelope -
 * the single place HTTP status codes and client-facing messages are decided, so no controller
 * needs its own try/catch. Internal details (RailRadar's name, raw exception messages, stack
 * traces) never reach the client; they're logged here instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A path variable failed its {@code @Pattern}/{@code @Min} etc. constraint - bad client input. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, "INVALID_TRAIN_NUMBER", message, request);
    }

    /** A query parameter couldn't be converted to its expected type (bad enum value, unparseable
     * timestamp, etc.) - bad client input, same status/category as a filter that fails its own
     * cross-field validation (see {@link InvalidAccuracyFilterException}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, "INVALID_FILTER", message, request);
    }

    /** The accuracy-report filter itself is unsatisfiable (Phase 16H-6) - e.g. an inverted time
     * range. Bad client input, not a server fault. */
    @ExceptionHandler(InvalidAccuracyFilterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccuracyFilter(
            InvalidAccuracyFilterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_FILTER", ex.getMessage(), request);
    }

    @ExceptionHandler(TrainNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTrainNotFound(TrainNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "TRAIN_NOT_FOUND", ex.getMessage(), request);
    }

    /** The train's live data is valid, but there's nothing future left to predict. */
    @ExceptionHandler(PredictionNotApplicableException.class)
    public ResponseEntity<ErrorResponse> handlePredictionNotApplicable(
            PredictionNotApplicableException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "PREDICTION_NOT_APPLICABLE", ex.getMessage(), request);
    }

    /** RailRadar responded, but the body was unusable - a bad response from upstream. */
    @ExceptionHandler(MalformedRailRadarResponseException.class)
    public ResponseEntity<ErrorResponse> handleMalformedResponse(
            MalformedRailRadarResponseException ex, HttpServletRequest request) {
        log.warn("Malformed RailRadar response for {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "INVALID_TRAIN_DATA",
                "The train data service returned invalid data. Please try again later.", request);
    }

    /**
     * Any other RailRadar failure not handled above - unreachable, timed out, or rejected our API
     * key ({@link RailRadarUnavailableException}, {@link RailRadarAuthenticationException}, or any
     * future subtype). The client can't act differently on any of these, so they share one status
     * and message; the specific cause is logged, never returned.
     */
    @ExceptionHandler(RailRadarException.class)
    public ResponseEntity<ErrorResponse> handleRailRadarUnavailable(RailRadarException ex, HttpServletRequest request) {
        log.warn("RailRadar unavailable for {}: category={}, detail={}",
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_SERVICE_UNAVAILABLE",
                "The train data service is temporarily unavailable. Please try again later.", request);
    }

    /** Last resort: never let a raw stack trace or exception message reach a client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", request);
    }

    private static ResponseEntity<ErrorResponse> build(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
