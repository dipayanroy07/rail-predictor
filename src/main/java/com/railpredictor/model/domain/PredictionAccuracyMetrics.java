package com.railpredictor.model.domain;

/**
 * Aggregate accuracy statistics over a set of signed prediction errors (Phase 16H-5) - see
 * {@code PredictionAccuracyCalculator}, the only thing that computes this.
 *
 * <p><b>Sign convention</b>: {@code error = predicted - actual}, preserved unclamped throughout -
 * a positive mean signals the model tends to over-predict delay, a negative mean under-predict.
 *
 * <ul>
 *   <li>{@code meanAbsoluteError} (MAE) = {@code mean(abs(error))} - typical error magnitude,
 *       ignoring direction.</li>
 *   <li>{@code rootMeanSquaredError} (RMSE) = {@code sqrt(mean(error^2))} - like MAE but penalizes
 *       large errors more heavily.</li>
 *   <li>{@code bias} = {@code mean(error)} - systematic over/under-prediction; near zero means
 *       errors roughly cancel out, not that individual predictions were accurate.</li>
 * </ul>
 *
 * <p>{@code sampleCount == 0} produces {@code 0.0} for every statistic - a placeholder, not a
 * measured "zero error" - matching this codebase's existing empty-data convention (see e.g.
 * {@code HistoricalDelayProfileAggregator}).
 */
public record PredictionAccuracyMetrics(
        int sampleCount,
        double meanAbsoluteError,
        double rootMeanSquaredError,
        double bias) {

    public PredictionAccuracyMetrics {
        Guard.requireNonNegative(sampleCount, "sampleCount");
        Guard.requireNonNegative(meanAbsoluteError, "meanAbsoluteError");
        Guard.requireNonNegative(rootMeanSquaredError, "rootMeanSquaredError");
    }
}
