package com.railpredictor.evaluation;

import com.railpredictor.config.HistoricalCollectionProperties;
import com.railpredictor.exception.PredictionNotApplicableException;
import com.railpredictor.service.PredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The independent, scheduled real-prediction-collection path Phase 22F introduces - calls the
 * real {@link PredictionService} for each explicitly configured train number (reusing
 * {@link HistoricalCollectionProperties#trainNumbers()}, the same operator-maintained list Phase
 * 22B's historical-observation collector already uses), so {@code prediction_snapshots}
 * accumulates autonomously instead of only ever growing from a manually-called live endpoint.
 *
 * <p><b>Reuses the existing prediction pipeline exactly as-is</b>: this class never recomputes a
 * prediction, never builds a {@code PredictionSnapshot} itself, and never touches persistence
 * directly - it only calls {@link PredictionService#getPrediction(String)}, exactly like
 * {@code PredictionController} does for a live request. Snapshot recording (best-effort, gated on
 * {@code prediction.evaluation.enabled}), quarantine handling, and every existing validation rule
 * are therefore inherited unchanged - there is no second, parallel snapshot-creation code path to
 * keep in sync.
 *
 * <p>A train with no next station right now ({@link PredictionNotApplicableException} - the same
 * condition {@code PredictionController} maps to HTTP 422) is an expected, non-failure outcome:
 * no snapshot is created for it (exactly as a real 422 API response never creates one), and it is
 * counted separately from a genuine failure - see {@link PredictionCollectionResult}.
 *
 * <p>Every train is attempted independently: one train's failure (RailRadar unavailable, no route
 * found, any other {@link RuntimeException}) is logged and does not prevent the remaining
 * configured trains from being attempted - mirroring
 * {@code HistoricalObservationCollectionService#collectOne} exactly.
 */
@Component
public class PredictionCollectionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionCollectionService.class);

    private final PredictionService predictionService;
    private final HistoricalCollectionProperties trainListProperties;

    public PredictionCollectionService(
            PredictionService predictionService, HistoricalCollectionProperties trainListProperties) {
        this.predictionService = predictionService;
        this.trainListProperties = trainListProperties;
    }

    /** Attempts every configured train number once, independently. Never throws - a failure for
     * one train is captured in the returned counts, not propagated. */
    public PredictionCollectionResult collectAll() {
        int predicted = 0;
        int notApplicable = 0;
        int failed = 0;
        for (String trainNumber : trainListProperties.trainNumbers()) {
            switch (collectOne(trainNumber)) {
                case PREDICTED -> predicted++;
                case NOT_APPLICABLE -> notApplicable++;
                case FAILED -> failed++;
            }
        }
        return new PredictionCollectionResult(
                trainListProperties.trainNumbers().size(), predicted, notApplicable, failed);
    }

    private Outcome collectOne(String trainNumber) {
        try {
            predictionService.getPrediction(trainNumber);
            return Outcome.PREDICTED;
        } catch (PredictionNotApplicableException e) {
            log.debug("Prediction collection skipped for train {} - no next station: {}", trainNumber, e.getMessage());
            return Outcome.NOT_APPLICABLE;
        } catch (RuntimeException e) {
            log.warn("Prediction collection failed for train {}: {}", trainNumber, e.getMessage());
            return Outcome.FAILED;
        }
    }

    private enum Outcome {
        PREDICTED, NOT_APPLICABLE, FAILED
    }
}
