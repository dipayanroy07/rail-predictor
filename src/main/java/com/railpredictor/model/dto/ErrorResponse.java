package com.railpredictor.model.dto;

import java.time.Instant;

/**
 * The consistent error envelope every failed API request returns, built by
 * {@code GlobalExceptionHandler}. {@code message} is always safe to show a caller - never a raw
 * exception message or stack trace.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
