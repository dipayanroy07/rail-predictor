package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.repository.HistoricalObservationEntity;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HistoricalObservationRecorderTest {

    private final HistoricalDataMetrics metrics = new HistoricalDataMetrics();

    private static HistoricalObservation observation() {
        return new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5,
                "20:39", "20:51", "20:41", "20:53", 12, 12,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);
    }

    /** Origin-shaped: departure known, no arrival - see HistoricalObservationMapper's Phase 16E
     * eligibility fix. */
    private static HistoricalObservation originObservation(Integer sequence) {
        return new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "NDLS", sequence,
                null, null, "08:00", "08:05", null, 5,
                Instant.parse("2026-09-09T02:35:00Z"), DataProvenance.RAILRADAR);
    }

    @Test
    void doesNothingWhenNoRepositoryIsConfigured() {
        HistoricalObservationRecorder recorder =
                new HistoricalObservationRecorder(Optional.empty(), new HistoricalObservationEntityMapper(), metrics);

        assertThatCode(() -> recorder.recordAll(List.of(observation()))).doesNotThrowAnyException();
    }

    @Test
    void insertsANewRowWhenNoCanonicalRecordExistsYet() {
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5)).thenReturn(Optional.empty());
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);

        recorder.recordAll(List.of(observation()));

        verify(repository).save(any(HistoricalObservationEntity.class));
        assertThat(metrics.observationsInserted()).isEqualTo(1);
        assertThat(metrics.observationsUpdated()).isZero();
    }

    @Test
    void updatesTheExistingCanonicalRowInsteadOfInsertingADuplicate() {
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        HistoricalObservationEntity existing = new HistoricalObservationEntityMapper().toEntity(observation());
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5)).thenReturn(Optional.of(existing));
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);

        recorder.recordAll(List.of(observation()));

        // Exactly one save - the same (found) entity is updated in place, never a second row.
        verify(repository, times(1)).save(existing);
        assertThat(metrics.observationsUpdated()).isEqualTo(1);
        assertThat(metrics.observationsInserted()).isZero();
    }

    @Test
    void processesEveryObservationInTheGivenList() {
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);
        HistoricalObservation second = new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "RTM", 6,
                "22:10", "22:12", "22:12", "22:14", 2, 2,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);

        recorder.recordAll(List.of(observation(), second));

        verify(repository, times(2)).save(any(HistoricalObservationEntity.class));
        assertThat(metrics.observationsInserted()).isEqualTo(2);
    }

    @Test
    void aConcurrentDuplicateInsertIsIgnoredRatherThanPropagated() {
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5)).thenReturn(Optional.empty());
        when(repository.save(any(HistoricalObservationEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);

        assertThatCode(() -> recorder.recordAll(List.of(observation()))).doesNotThrowAnyException();
        assertThat(metrics.persistenceFailures()).isEqualTo(1);
    }

    @Test
    void anOriginObservationWithNoArrivalPersistsSuccessfully() {
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "NDLS", 1)).thenReturn(Optional.empty());
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);

        recorder.recordAll(List.of(originObservation(1)));

        verify(repository).save(any(HistoricalObservationEntity.class));
        assertThat(metrics.observationsInserted()).isEqualTo(1);
    }

    @Test
    void repeatedPollingOfAStationWithNullSequenceUpdatesInPlaceRatherThanDuplicating() {
        // Spring Data JPA's derived query correctly translates a null argument into
        // "station_sequence IS NULL", so the same natural-key lookup already used for sequenced
        // stations also finds a previously-persisted null-sequence row - see
        // HistoricalObservationRepositoryTest for the equivalent proof against a real (H2) engine.
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        HistoricalObservationEntity existing = new HistoricalObservationEntityMapper().toEntity(originObservation(null));
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "NDLS", null)).thenReturn(Optional.of(existing));
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);

        recorder.recordAll(List.of(originObservation(null)));

        verify(repository, times(1)).save(existing);
        assertThat(metrics.observationsUpdated()).isEqualTo(1);
        assertThat(metrics.observationsInserted()).isZero();
    }

    @Test
    void twoObservationsSharingTheSameJourneyDateAfterTheOvernightMitigationCollapseIntoOneRow() {
        // Simulates two polls of the same physical (overnight) journey that, thanks to
        // HistoricalObservationMapper's operating-day-start-hour mitigation, were both assigned
        // the same journeyDate - the recorder must treat the second as an update, not a new row.
        HistoricalObservationRepository repository = mock(HistoricalObservationRepository.class);
        HistoricalObservationEntity firstPoll = new HistoricalObservationEntityMapper().toEntity(originObservation(1));
        when(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "NDLS", 1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(firstPoll));
        HistoricalObservationRecorder recorder = new HistoricalObservationRecorder(
                Optional.of(repository), new HistoricalObservationEntityMapper(), metrics);

        recorder.recordAll(List.of(originObservation(1)));
        recorder.recordAll(List.of(originObservation(1)));

        assertThat(metrics.observationsInserted()).isEqualTo(1);
        assertThat(metrics.observationsUpdated()).isEqualTo(1);
    }
}
