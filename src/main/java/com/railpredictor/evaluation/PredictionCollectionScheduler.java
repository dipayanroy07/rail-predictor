package com.railpredictor.evaluation;

import com.railpredictor.config.HistoricalCollectionProperties;
import com.railpredictor.config.PredictionCollectionProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The one thing in this codebase that actually calls {@link PredictionCollectionService} - on a
 * fixed schedule, independent of any manually-issued live prediction request (Phase 22F, mirroring
 * {@code HistoricalObservationCollectionScheduler}'s established pattern exactly). This is what
 * lets {@code prediction_snapshots} accumulate on its own, rather than only growing whenever
 * someone happens to call the live prediction endpoint.
 *
 * <p>A no-op whenever collection is disabled ({@code prediction.collection.enabled=false}, the
 * default) or no train numbers are configured (reusing
 * {@code historical.collection.train-numbers}) - checked directly, so the common case is a quiet
 * no-op rather than a logged failure on every tick.
 *
 * <p><b>Overlap protection</b>: a plain in-process {@link AtomicBoolean} guard, exactly like
 * {@code HistoricalObservationCollectionScheduler} - if a previous run is still executing when the
 * next tick fires, this tick is skipped rather than starting a second, overlapping run. Process-
 * local only, not a distributed lock - same accepted limitation as the historical collector.
 */
@Component
public class PredictionCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PredictionCollectionScheduler.class);

    private final PredictionCollectionProperties properties;
    private final HistoricalCollectionProperties trainListProperties;
    private final PredictionCollectionService collectionService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PredictionCollectionScheduler(
            PredictionCollectionProperties properties,
            HistoricalCollectionProperties trainListProperties,
            PredictionCollectionService collectionService) {
        this.properties = properties;
        this.trainListProperties = trainListProperties;
        this.collectionService = collectionService;
    }

    @Scheduled(
            fixedDelayString = "${prediction.collection.interval-ms:3600000}",
            initialDelayString = "${prediction.collection.initial-delay-ms:600000}")
    public void collect() {
        if (!properties.enabled()) {
            log.debug("Scheduled prediction-snapshot collection is disabled - skipping");
            return;
        }
        if (trainListProperties.trainNumbers().isEmpty()) {
            log.debug("Scheduled prediction-snapshot collection is enabled but no train numbers are configured "
                    + "(historical.collection.train-numbers) - skipping");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("A previous prediction-snapshot collection run is still executing - skipping this tick");
            return;
        }
        try {
            PredictionCollectionResult result = collectionService.collectAll();
            log.info(
                    "Prediction-snapshot collection run complete: {} train(s) attempted, {} predicted, "
                            + "{} not applicable (no next station), {} failed",
                    result.trainsAttempted(), result.predicted(), result.notApplicable(), result.failed());
        } catch (RuntimeException e) {
            log.warn("Prediction-snapshot collection run failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
