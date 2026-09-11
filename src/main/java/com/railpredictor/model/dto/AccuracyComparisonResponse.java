package com.railpredictor.model.dto;

/**
 * The model's accuracy alongside the "current-delay-only" baseline's, over the same sample
 * (Phase 16H-6). See {@code PredictionAccuracyComparison} for the baseline's exact definition.
 */
public record AccuracyComparisonResponse(AccuracyMetricsResponse currentModel, AccuracyMetricsResponse baseline) {
}
