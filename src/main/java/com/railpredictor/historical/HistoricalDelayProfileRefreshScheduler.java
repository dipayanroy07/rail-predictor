package com.railpredictor.historical;

import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.TrainStationKey;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The one thing in this codebase that actually calls {@link HistoricalDelayProfileRefresher} and
 * (Phase 16F) {@link HistoricalSectionDelayProfileRefresher} - on a fixed schedule, independent of
 * any prediction request (Phase 16C, item 2/6). This is what makes "prediction consumes
 * historical data, it does not maintain it" true in practice:
 * {@link com.railpredictor.railradar.RailRadarTrainDataProvider} only ever records raw
 * observations (piggybacked on the live-prediction RailRadar call, since a second RailRadar call
 * just to poll for historical data would burn extra quota for no benefit - see
 * docs/historical-data-design.md for the full "smallest safe approach" reasoning); this scheduler
 * is the only path that turns those raw observations into refreshed profiles of either shape.
 *
 * <p>Deliberately checks for the observation repository itself (rather than calling either
 * refresher and catching its {@link IllegalStateException}) so that the common "no database
 * configured at all" case is a quiet, expected no-op rather than a logged failure on every tick.
 *
 * <p>Each (trainNumber, stationCode) pair, and (Phase 16F) each trainNumber's section profiles,
 * is refreshed independently, inside its own try/catch - one bad pair/train (e.g. a transient DB
 * error) must not prevent every other one from refreshing.
 */
@Component
public class HistoricalDelayProfileRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDelayProfileRefreshScheduler.class);

    private final Optional<HistoricalObservationRepository> observationRepository;
    private final HistoricalDelayProfileRefresher refresher;
    private final HistoricalSectionDelayProfileRefresher sectionRefresher;
    private final HistoricalDataMetrics metrics;

    public HistoricalDelayProfileRefreshScheduler(
            Optional<HistoricalObservationRepository> observationRepository,
            HistoricalDelayProfileRefresher refresher,
            HistoricalSectionDelayProfileRefresher sectionRefresher,
            HistoricalDataMetrics metrics) {
        this.observationRepository = observationRepository;
        this.refresher = refresher;
        this.sectionRefresher = sectionRefresher;
        this.metrics = metrics;
    }

    @Scheduled(
            fixedDelayString = "${historical.profile-refresh.interval-ms:3600000}",
            initialDelayString = "${historical.profile-refresh.initial-delay-ms:60000}")
    public void refreshAllProfiles() {
        if (observationRepository.isEmpty()) {
            log.debug("No historical observation database configured - skipping profile refresh");
            return;
        }

        List<TrainStationKey> keys = observationRepository.get().findDistinctTrainStationKeys();
        log.debug("Refreshing {} historical delay profile(s)", keys.size());
        for (TrainStationKey key : keys) {
            refreshOne(key);
        }

        List<String> trainNumbers = observationRepository.get().findDistinctTrainNumbers();
        log.debug("Refreshing section delay profiles for {} train(s)", trainNumbers.size());
        for (String trainNumber : trainNumbers) {
            refreshSectionsForTrain(trainNumber);
        }
    }

    private void refreshOne(TrainStationKey key) {
        try {
            HistoricalDelayProfile profile = refresher.refreshProfile(key.trainNumber(), key.stationCode());
            metrics.profileGenerated();
            log.debug(
                    "Refreshed historical delay profile for train {} station {}: {} sample(s)",
                    key.trainNumber(), key.stationCode(), profile.sampleCount());
        } catch (RuntimeException e) {
            metrics.profileRefreshFailure();
            log.warn(
                    "Failed to refresh historical delay profile for train {} station {}: {}",
                    key.trainNumber(), key.stationCode(), e.getMessage());
        }
    }

    private void refreshSectionsForTrain(String trainNumber) {
        try {
            List<HistoricalSectionDelayProfile> profiles =
                    sectionRefresher.refreshSectionProfilesForTrain(trainNumber);
            metrics.sectionProfileGenerated();
            log.debug(
                    "Refreshed {} section delay profile(s) for train {}",
                    profiles.size(), trainNumber);
        } catch (RuntimeException e) {
            metrics.sectionProfileRefreshFailure();
            log.warn(
                    "Failed to refresh section delay profiles for train {}: {}",
                    trainNumber, e.getMessage());
        }
    }
}
