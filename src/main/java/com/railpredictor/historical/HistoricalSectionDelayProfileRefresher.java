package com.railpredictor.historical;

import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.HistoricalSectionDelayProfileEntity;
import com.railpredictor.repository.HistoricalSectionDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalSectionDelayProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges {@link HistoricalSectionDelayProfileAggregator} (pure statistics) to persistence: loads
 * every observation recorded for one train, aggregates every (fromStationCode, toStationCode)
 * adjacency it discovers, and upserts each resulting {@link HistoricalSectionDelayProfile} - one
 * canonical row per (trainNumber, fromStationCode, toStationCode), re-computed in place rather
 * than versioned. Mirrors {@link HistoricalDelayProfileRefresher}'s exact pattern for the
 * station-level profile.
 *
 * <p>Not called automatically from any live request path - see
 * {@code HistoricalDelayProfileRefreshScheduler}, which is the only caller (Phase 16F extends it
 * to also refresh section profiles, on the same schedule). Fails loudly (an
 * {@link IllegalStateException}) rather than silently no-op-ing when either database isn't
 * configured - the same "a deliberate refresh call deserves to know it didn't happen" reasoning as
 * the station-level refresher.
 *
 * <p>The reference instant used for the (currently unused-by-default) point-in-time cutoff is
 * "now" (via the injected {@link Clock}) unless a caller supplies a different one via
 * {@link #refreshSectionProfilesForTrain(String, Instant)} - see
 * {@link HistoricalSectionDelayProfileAggregator} for the cutoff's exact inclusive semantics.
 */
@Component
public class HistoricalSectionDelayProfileRefresher {

    private final Optional<HistoricalObservationRepository> observationRepository;
    private final Optional<HistoricalSectionDelayProfileRepository> profileRepository;
    private final HistoricalObservationEntityMapper observationEntityMapper;
    private final HistoricalSectionDelayProfileEntityMapper profileEntityMapper;
    private final HistoricalSectionDelayProfileAggregator aggregator;
    private final Clock clock;

    public HistoricalSectionDelayProfileRefresher(
            Optional<HistoricalObservationRepository> observationRepository,
            Optional<HistoricalSectionDelayProfileRepository> profileRepository,
            HistoricalObservationEntityMapper observationEntityMapper,
            HistoricalSectionDelayProfileEntityMapper profileEntityMapper,
            HistoricalSectionDelayProfileAggregator aggregator,
            Clock clock) {
        this.observationRepository = observationRepository;
        this.profileRepository = profileRepository;
        this.observationEntityMapper = observationEntityMapper;
        this.profileEntityMapper = profileEntityMapper;
        this.aggregator = aggregator;
        this.clock = clock;
    }

    /** Refreshes every section profile discoverable for this train, as of now. */
    @Transactional
    public List<HistoricalSectionDelayProfile> refreshSectionProfilesForTrain(String trainNumber) {
        return refreshSectionProfilesForTrain(trainNumber, Instant.now(clock));
    }

    /** Refreshes every section profile discoverable for this train, using only observations at or
     * before {@code referenceInstant} (see {@link HistoricalSectionDelayProfileAggregator} for the
     * exact inclusive cutoff semantics) - the hook a future backtesting caller needs, per Phase
     * 16F's design; nothing in this phase supplies a cutoff other than "now". */
    @Transactional
    public List<HistoricalSectionDelayProfile> refreshSectionProfilesForTrain(String trainNumber, Instant referenceInstant) {
        HistoricalObservationRepository observations = observationRepository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot refresh section delay profiles: no observation database is configured "
                                + "(activate the \"postgres\" Spring profile - see docs/configuration.md)"));
        HistoricalSectionDelayProfileRepository profiles = profileRepository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot refresh section delay profiles: no section-profile database is configured "
                                + "(activate the \"postgres\" Spring profile - see docs/configuration.md)"));

        List<HistoricalObservation> rawObservations = observations.findByTrainNumber(trainNumber).stream()
                .map(observationEntityMapper::toDomain)
                .toList();

        List<HistoricalSectionDelayProfile> sectionProfiles =
                aggregator.aggregate(trainNumber, rawObservations, referenceInstant);
        sectionProfiles.forEach(profile -> upsert(profiles, profile));
        return sectionProfiles;
    }

    private void upsert(HistoricalSectionDelayProfileRepository profiles, HistoricalSectionDelayProfile profile) {
        HistoricalSectionDelayProfileEntity latest = profileEntityMapper.toEntity(profile);
        profiles.findByTrainNumberAndFromStationCodeAndToStationCode(
                        profile.trainNumber(), profile.fromStationCode(), profile.toStationCode())
                .ifPresentOrElse(
                        existing -> {
                            existing.updateFrom(latest);
                            profiles.save(existing);
                        },
                        () -> profiles.save(latest));
    }
}
