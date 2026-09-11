package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.repository.HistoricalObservationEntity;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PredictionEvaluationRefresherTest {

    private final PredictionSnapshotRepository snapshotRepository = mock(PredictionSnapshotRepository.class);
    private final HistoricalObservationRepository observationRepository = mock(HistoricalObservationRepository.class);
    private final PredictionSnapshotEntityMapper snapshotEntityMapper = new PredictionSnapshotEntityMapper();
    private final HistoricalObservationEntityMapper observationEntityMapper = new HistoricalObservationEntityMapper();
    private final PredictionOutcomeMatcher matcher = new PredictionOutcomeMatcher();

    private PredictionEvaluationRefresher refresher() {
        return new PredictionEvaluationRefresher(
                Optional.of(snapshotRepository), Optional.of(observationRepository),
                snapshotEntityMapper, observationEntityMapper, matcher);
    }

    private static PredictionSnapshotEntity pendingEntity(String trainNumber, String stationCode) {
        return new PredictionSnapshotEntity(
                trainNumber, Instant.parse("2026-09-09T10:00:00Z"), stationCode,
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, "STATION_FALLBACK", DataProvenance.RAILRADAR, 75.0,
                "PENDING", null, null, null);
    }

    private static HistoricalObservationEntity observationEntity(
            String trainNumber, String stationCode, Integer arrivalDelay, Instant observedAt) {
        return new HistoricalObservationEntity(
                trainNumber, LocalDate.of(2026, 9, 9), stationCode, 5,
                "10:00", "10:05", "10:07", "10:09",
                arrivalDelay, arrivalDelay, observedAt, DataProvenance.RAILRADAR);
    }

    @Test
    void throwsWhenNoSnapshotRepositoryIsConfigured() {
        PredictionEvaluationRefresher refresher = new PredictionEvaluationRefresher(
                Optional.empty(), Optional.of(observationRepository),
                snapshotEntityMapper, observationEntityMapper, matcher);

        assertThrows(IllegalStateException.class, refresher::evaluatePendingSnapshots);
    }

    @Test
    void throwsWhenNoObservationRepositoryIsConfigured() {
        PredictionEvaluationRefresher refresher = new PredictionEvaluationRefresher(
                Optional.of(snapshotRepository), Optional.empty(),
                snapshotEntityMapper, observationEntityMapper, matcher);

        assertThrows(IllegalStateException.class, refresher::evaluatePendingSnapshots);
    }

    @Test
    void evaluatesAPendingSnapshotWithAMatchingFutureObservation() {
        when(snapshotRepository.findByEvaluationStatus("PENDING"))
                .thenReturn(List.of(pendingEntity("12952", "KOTA")));
        when(observationRepository.findByTrainNumberAndStationCode("12952", "KOTA"))
                .thenReturn(List.of(observationEntity("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"))));

        int evaluatedCount = refresher().evaluatePendingSnapshots();

        assertThat(evaluatedCount).isEqualTo(1);
    }

    @Test
    void leavesASnapshotPendingWhenNoMatchingObservationExistsYet() {
        when(snapshotRepository.findByEvaluationStatus("PENDING"))
                .thenReturn(List.of(pendingEntity("12952", "KOTA")));
        when(observationRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(List.of());

        int evaluatedCount = refresher().evaluatePendingSnapshots();

        assertThat(evaluatedCount).isZero();
    }

    @Test
    void noPendingSnapshotsMeansNoObservationLookupsAtAll() {
        when(snapshotRepository.findByEvaluationStatus("PENDING")).thenReturn(List.of());

        int evaluatedCount = refresher().evaluatePendingSnapshots();

        assertThat(evaluatedCount).isZero();
        org.mockito.Mockito.verify(observationRepository, org.mockito.Mockito.never())
                .findByTrainNumberAndStationCode(any(), any());
    }

    @Test
    void aSecondRunNeverReEvaluatesASnapshotAlreadyEvaluatedByTheFirstRun() {
        // The repository is the source of truth for "still PENDING" - once a real database has
        // applied the first run's evaluation, a genuinely PENDING-status query on a second run
        // simply would not return that row any more. Modeling that here with a two-call stub
        // proves the refresher's own logic never re-touches a row it isn't handed as PENDING.
        PredictionSnapshotEntity entity = pendingEntity("12952", "KOTA");
        when(snapshotRepository.findByEvaluationStatus("PENDING"))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(observationRepository.findByTrainNumberAndStationCode("12952", "KOTA"))
                .thenReturn(List.of(observationEntity("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"))));

        PredictionEvaluationRefresher refresher = refresher();
        int firstRun = refresher.evaluatePendingSnapshots();
        int secondRun = refresher.evaluatePendingSnapshots();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        // The first run's outcome must survive untouched - a second run must never overwrite it.
        assertThat(entity.getEvaluationStatus()).isEqualTo("EVALUATED_EXACT");
        assertThat(entity.getActualDelayMinutes()).isEqualTo(6);
    }
}
