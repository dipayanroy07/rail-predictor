package com.railpredictor.historical;

import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.repository.HistoricalDelayProfileEntity;
import com.railpredictor.repository.HistoricalDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalDelayProfileRepository;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges {@link HistoricalDelayProfileAggregator} (pure statistics) to persistence: loads every
 * observation for a (train, station), aggregates it, and upserts the resulting
 * {@link HistoricalDelayProfile} - one canonical row per (trainNumber, stationCode), re-computed
 * in place rather than versioned, exactly like {@code HistoricalObservationRecorder}'s own
 * idempotent pattern (Phase 16A).
 *
 * <p>Unlike {@code HistoricalObservationRecorder}, this is <b>not</b> called automatically from
 * any live request path - refreshing a profile is an explicit, administrative operation (this
 * phase establishes the capability; deciding when/how to trigger it - a scheduled job, an admin
 * endpoint, on demand - is deferred). Because it's only ever invoked deliberately, it fails loudly
 * (an {@link IllegalStateException}) rather than silently no-op-ing when the database isn't
 * configured - a caller explicitly asking for a refresh deserves to know it didn't happen.
 *
 * <p><b>Temporal-leakage limitation</b> (see docs/historical-data-design.md): this loads <em>all</em>
 * observations ever recorded for the (train, station) pair, with no point-in-time cutoff. A
 * profile refreshed this way is only appropriate for "what does history look like as of right
 * now" - it must not be used to predict a past journey for backtesting/accuracy validation
 * without first adding a cutoff (observedAt/journeyDate before the prediction's own point in
 * time), which this phase does not implement.
 */
@Component
public class HistoricalDelayProfileRefresher {

    private final Optional<HistoricalObservationRepository> observationRepository;
    private final Optional<HistoricalDelayProfileRepository> profileRepository;
    private final HistoricalObservationEntityMapper observationEntityMapper;
    private final HistoricalDelayProfileEntityMapper profileEntityMapper;
    private final HistoricalDelayProfileAggregator aggregator;

    public HistoricalDelayProfileRefresher(
            Optional<HistoricalObservationRepository> observationRepository,
            Optional<HistoricalDelayProfileRepository> profileRepository,
            HistoricalObservationEntityMapper observationEntityMapper,
            HistoricalDelayProfileEntityMapper profileEntityMapper,
            HistoricalDelayProfileAggregator aggregator) {
        this.observationRepository = observationRepository;
        this.profileRepository = profileRepository;
        this.observationEntityMapper = observationEntityMapper;
        this.profileEntityMapper = profileEntityMapper;
        this.aggregator = aggregator;
    }

    @Transactional
    public HistoricalDelayProfile refreshProfile(String trainNumber, String stationCode) {
        HistoricalObservationRepository observations = observationRepository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot refresh a historical delay profile: no observation database is configured "
                                + "(activate the \"postgres\" Spring profile - see docs/configuration.md)"));
        HistoricalDelayProfileRepository profiles = profileRepository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot refresh a historical delay profile: no profile database is configured "
                                + "(activate the \"postgres\" Spring profile - see docs/configuration.md)"));

        List<HistoricalObservation> rawObservations = observations.findByTrainNumberAndStationCode(trainNumber, stationCode)
                .stream()
                .map(observationEntityMapper::toDomain)
                .toList();

        HistoricalDelayProfile profile = aggregator.aggregate(trainNumber, stationCode, rawObservations);
        upsert(profiles, profile);
        return profile;
    }

    private void upsert(HistoricalDelayProfileRepository profiles, HistoricalDelayProfile profile) {
        HistoricalDelayProfileEntity latest = profileEntityMapper.toEntity(profile);
        profiles.findByTrainNumberAndStationCode(profile.trainNumber(), profile.stationCode())
                .ifPresentOrElse(
                        existing -> {
                            existing.updateFrom(latest);
                            profiles.save(existing);
                        },
                        () -> profiles.save(latest));
    }
}
