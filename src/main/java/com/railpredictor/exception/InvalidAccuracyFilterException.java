package com.railpredictor.exception;

/**
 * The accuracy-report filter (Phase 16H-6) is structurally unsatisfiable - e.g. a
 * {@code predictionMadeFrom} after {@code predictionMadeTo}. Not a server fault: maps to HTTP 400,
 * distinct from "no evaluated data matched" (a valid, non-error report result).
 */
public class InvalidAccuracyFilterException extends RuntimeException {

    public InvalidAccuracyFilterException(String message) {
        super(message);
    }
}
