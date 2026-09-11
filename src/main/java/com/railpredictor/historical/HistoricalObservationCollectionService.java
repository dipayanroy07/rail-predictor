package com.railpredictor.historical;

import com.railpredictor.config.HistoricalCollectionProperties;
import com.railpredictor.railradar.TrainDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The independent, scheduled real-data collection path Phase 22B introduces - fetches live
 * RailRadar data for each explicitly configured train number, for the sole purpose of the
 * historical-observation recording that already happens as a side effect of
 * {@link TrainDataProvider#getLiveTrainData(String)} (see {@code RailRadarTrainDataProvider}).
 *
 * <p><b>This class deliberately does nothing else</b>: it never runs a prediction, never builds a
 * {@code PredictionResult}, never creates a {@code PredictionSnapshot}, and never evaluates
 * anything - collection is not prediction. It reuses the existing
 * {@link TrainDataProvider}/{@code HistoricalObservationMapper}/{@code HistoricalObservationRecorder}
 * pipeline exactly as-is (no duplicated RailRadar client logic, no second HTTP call), discarding
 * the returned {@code LiveTrainData} once the recording side effect has happened.
 *
 * <p>Every train is attempted independently: one train's failure (not found, RailRadar
 * unavailable, malformed response) is logged and does not prevent the remaining configured trains
 * from being attempted - see {@link #collectOne}.
 */
@Component
public class HistoricalObservationCollectionService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalObservationCollectionService.class);

    private final TrainDataProvider trainDataProvider;
    private final HistoricalCollectionProperties properties;

    public HistoricalObservationCollectionService(
            TrainDataProvider trainDataProvider, HistoricalCollectionProperties properties) {
        this.trainDataProvider = trainDataProvider;
        this.properties = properties;
    }

    /** Attempts every configured train number once, independently. Never throws - a failure for
     * one train is captured in the returned counts, not propagated. */
    public HistoricalObservationCollectionResult collectAll() {
        int succeeded = 0;
        int failed = 0;
        for (String trainNumber : properties.trainNumbers()) {
            if (collectOne(trainNumber)) {
                succeeded++;
            } else {
                failed++;
            }
        }
        return new HistoricalObservationCollectionResult(properties.trainNumbers().size(), succeeded, failed);
    }

    /** @return whether the fetch succeeded - the resulting {@code LiveTrainData} itself is
     *         intentionally discarded; only the historical-observation side effect matters here. */
    private boolean collectOne(String trainNumber) {
        try {
            trainDataProvider.getLiveTrainData(trainNumber);
            return true;
        } catch (RuntimeException e) {
            log.warn("Historical observation collection failed for train {}: {}", trainNumber, e.getMessage());
            return false;
        }
    }
}
