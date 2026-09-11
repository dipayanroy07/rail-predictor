package com.railpredictor.model.dto;

/**
 * JSON shape of {@code PredictionAccuracyMetrics} (Phase 16H-6). {@code error = predicted -
 * actual}: positive {@code bias} means the model tends to over-predict delay, negative means it
 * under-predicts. {@code sampleCount == 0} means every statistic is a {@code 0.0} placeholder, not
 * a measured "zero error".
 */
public record AccuracyMetricsResponse(
        int sampleCount, double meanAbsoluteError, double rootMeanSquaredError, double bias) {
}
