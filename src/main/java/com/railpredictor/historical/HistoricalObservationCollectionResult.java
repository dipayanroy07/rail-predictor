package com.railpredictor.historical;

/**
 * The outcome of one {@link HistoricalObservationCollectionService#collectAll()} run (Phase 22B) -
 * a lightweight, in-memory summary (never persisted) for logging and testing. Per-observation
 * detail (inserted/updated/rejected counts) is already tracked globally by
 * {@link HistoricalDataMetrics} - this record only tracks this run's own per-train fetch outcome,
 * which nothing else captures.
 */
public record HistoricalObservationCollectionResult(int trainsAttempted, int trainsSucceeded, int trainsFailed) {

    public HistoricalObservationCollectionResult {
        if (trainsAttempted < 0 || trainsSucceeded < 0 || trainsFailed < 0) {
            throw new IllegalArgumentException("trainsAttempted/trainsSucceeded/trainsFailed must not be negative");
        }
        if (trainsSucceeded + trainsFailed != trainsAttempted) {
            throw new IllegalArgumentException("trainsSucceeded + trainsFailed must equal trainsAttempted");
        }
    }
}
