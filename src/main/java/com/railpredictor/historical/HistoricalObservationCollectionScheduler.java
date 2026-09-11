package com.railpredictor.historical;

import com.railpredictor.config.HistoricalCollectionProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The one thing in this codebase that actually calls
 * {@link HistoricalObservationCollectionService} - on a fixed schedule, independent of live
 * prediction requests (Phase 22B). This is what finally makes historical-observation collection
 * <em>not</em> depend on someone repeatedly calling the prediction endpoint - the exact
 * architectural gap Phase 22A identified.
 *
 * <p>A no-op whenever collection is disabled ({@code historical.collection.enabled=false}, the
 * default) or no train numbers are configured - checked directly, so the common case is a quiet
 * no-op rather than a logged failure on every tick, mirroring
 * {@code PredictionEvaluationRefreshScheduler}'s own established pattern.
 *
 * <p><b>Overlap protection</b>: a plain in-process {@link AtomicBoolean} guard - if a previous run
 * is still executing when the next tick fires (e.g. RailRadar is slow, or many trains are
 * configured), this tick is skipped rather than starting a second, overlapping run. This is
 * <b>process-local only</b> - it does not coordinate across multiple application instances; a
 * distributed lock was deliberately not introduced for this phase (see docs/historical-data-design.md's
 * Phase 22B notes on why that would be premature).
 */
@Component
public class HistoricalObservationCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoricalObservationCollectionScheduler.class);

    private final HistoricalCollectionProperties properties;
    private final HistoricalObservationCollectionService collectionService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HistoricalObservationCollectionScheduler(
            HistoricalCollectionProperties properties, HistoricalObservationCollectionService collectionService) {
        this.properties = properties;
        this.collectionService = collectionService;
    }

    @Scheduled(
            fixedDelayString = "${historical.collection.interval-ms:3600000}",
            initialDelayString = "${historical.collection.initial-delay-ms:300000}")
    public void collect() {
        if (!properties.enabled()) {
            log.debug("Scheduled historical observation collection is disabled - skipping");
            return;
        }
        if (properties.trainNumbers().isEmpty()) {
            log.debug("Scheduled historical observation collection is enabled but no train numbers "
                    + "are configured (historical.collection.train-numbers) - skipping");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("A previous historical observation collection run is still executing - skipping this tick");
            return;
        }
        try {
            HistoricalObservationCollectionResult result = collectionService.collectAll();
            log.info(
                    "Historical observation collection run complete: {} train(s) attempted, {} succeeded, {} failed",
                    result.trainsAttempted(), result.trainsSucceeded(), result.trainsFailed());
        } catch (RuntimeException e) {
            log.warn("Historical observation collection run failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
