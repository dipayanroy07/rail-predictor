package com.railpredictor.evaluation;

/**
 * The outcome of one {@link PredictionCollectionService#collectAll()} run (Phase 22F) - a
 * lightweight, in-memory summary (never persisted) for logging and testing, mirroring
 * {@code HistoricalObservationCollectionResult}'s own shape.
 *
 * <p>{@code notApplicable} is deliberately its own bucket, distinct from {@code failed}: a train
 * with no next station right now ({@code PredictionNotApplicableException}) is an honest, expected
 * real-world outcome (already at its final stop, or terminated for the day), never a pipeline
 * failure - counting it as "failed" would misrepresent a perfectly healthy run as broken.
 */
public record PredictionCollectionResult(int trainsAttempted, int predicted, int notApplicable, int failed) {

    public PredictionCollectionResult {
        if (trainsAttempted < 0 || predicted < 0 || notApplicable < 0 || failed < 0) {
            throw new IllegalArgumentException("trainsAttempted/predicted/notApplicable/failed must not be negative");
        }
        if (predicted + notApplicable + failed != trainsAttempted) {
            throw new IllegalArgumentException("predicted + notApplicable + failed must equal trainsAttempted");
        }
    }
}
