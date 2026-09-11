package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the Phase 16H-5 prediction-accuracy-tracking foundation - deliberately
 * {@code enabled=false} by default: recording a snapshot on every live prediction (and later
 * evaluating it) is a genuinely new side effect of the live request path, and this codebase's own
 * pattern (e.g. {@code historical.provider} defaulting to {@code mock}) is to require an explicit
 * opt-in for new persistence behaviour rather than silently starting to write extra rows.
 */
@ConfigurationProperties(prefix = "prediction.evaluation")
public record PredictionEvaluationProperties(boolean enabled) {
}
