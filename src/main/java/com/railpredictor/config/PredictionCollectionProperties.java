package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the Phase 22F autonomous prediction-snapshot collection path -
 * {@link com.railpredictor.evaluation.PredictionCollectionScheduler} calling the real prediction
 * pipeline for each already-configured {@link HistoricalCollectionProperties#trainNumbers()} train,
 * so {@code prediction_snapshots} accumulates without an operator repeatedly calling the live
 * prediction endpoint by hand.
 *
 * <p>Deliberately has no {@code trainNumbers} of its own: it reuses
 * {@code historical.collection.train-numbers} rather than introducing a second, independently
 * operator-maintained train list that could drift out of sync with the one already used for
 * historical-observation collection - both concerns exist to build evaluation evidence for the
 * same set of trains.
 *
 * <p>{@code enabled} defaults to {@code false} - calling the real prediction pipeline on a
 * schedule (which itself calls {@code TrainDataProvider}, weather, historical, and
 * railway-disruption providers, and best-effort records a {@code PredictionSnapshot} when
 * {@code prediction.evaluation.enabled=true}) is a genuinely new scheduled side effect, requiring
 * the same explicit opt-in convention as {@code historical.collection.enabled}/
 * {@code prediction.evaluation.enabled}.
 */
@ConfigurationProperties(prefix = "prediction.collection")
public record PredictionCollectionProperties(boolean enabled, long intervalMs, long initialDelayMs) {

    public PredictionCollectionProperties {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("prediction.collection.interval-ms must be positive: " + intervalMs);
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "prediction.collection.initial-delay-ms must not be negative: " + initialDelayMs);
        }
    }
}
