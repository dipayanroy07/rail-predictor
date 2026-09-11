package com.railpredictor.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for {@link HistoricalObservationEntity}. Only ever wired up when the "postgres"
 * profile is active (see application.properties) - callers must treat it as optional, exactly
 * like {@code HistoricalObservationRecorder} does.
 */
public interface HistoricalObservationRepository extends JpaRepository<HistoricalObservationEntity, Long> {

    /** The natural key lookup used for idempotent upserts - see {@code HistoricalObservationRecorder}. */
    Optional<HistoricalObservationEntity> findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
            String trainNumber, LocalDate journeyDate, String stationCode, Integer stationSequence);

    List<HistoricalObservationEntity> findByTrainNumberAndJourneyDateOrderByStationSequenceAsc(
            String trainNumber, LocalDate journeyDate);

    /** Every observation ever recorded for this train at this station, across all journey dates -
     * the raw input to {@code HistoricalDelayProfileAggregator}. See docs/historical-data-design.md
     * on the temporal-leakage limitation this implies (no point-in-time cutoff is applied here). */
    List<HistoricalObservationEntity> findByTrainNumberAndStationCode(String trainNumber, String stationCode);

    /** Every distinct (trainNumber, stationCode) pair with at least one observation - how
     * {@code HistoricalDelayProfileRefreshScheduler} discovers which profiles to refresh. */
    @Query("SELECT DISTINCT new com.railpredictor.repository.TrainStationKey(o.trainNumber, o.stationCode) "
            + "FROM HistoricalObservationEntity o")
    List<TrainStationKey> findDistinctTrainStationKeys();

    /** Every observation ever recorded for this train, across every station and journey date -
     * the raw input to {@code HistoricalSectionDelayProfileAggregator} (Phase 16F), which does its
     * own journey-grouping/sequencing rather than relying on any pre-filtered query. Same
     * temporal-leakage caveat as {@code findByTrainNumberAndStationCode}: no point-in-time cutoff
     * is applied here - the aggregator applies its own, via {@code observedAt}. */
    List<HistoricalObservationEntity> findByTrainNumber(String trainNumber);

    /** Every distinct trainNumber with at least one observation - how
     * {@code HistoricalSectionDelayProfileRefresher}'s scheduled caller discovers which trains to
     * (re)compute section profiles for (Phase 16F). */
    @Query("SELECT DISTINCT o.trainNumber FROM HistoricalObservationEntity o")
    List<String> findDistinctTrainNumbers();
}
