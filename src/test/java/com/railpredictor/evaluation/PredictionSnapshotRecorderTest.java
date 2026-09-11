package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.model.domain.CascadeEffect;
import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.HistoricalAdjustmentResolution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PredictionSnapshotRecorderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC);
    private static final Station CURRENT_STATION = new Station("KOTA", "Kota Jn");
    private static final Station NEXT_STATION = new Station("RTM", "Ratlam Jn");

    private static PredictionResult resultWithNextStation(Station nextStation) {
        return resultWithNextStationAndWeatherProvenance(nextStation, null);
    }

    private static PredictionResult resultWithNextStationAndWeatherProvenance(Station nextStation, String weatherProvenance) {
        return new PredictionResult(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, CURRENT_STATION, nextStation,
                5, 465.0, 919.0, 92.5, SectionType.NORMAL, 90.0, 8, 3, 10, 23,
                Instant.parse("2026-09-09T11:41:00Z"),
                new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of(), List.of()),
                List.<DisruptionResult>of(), List.<CascadeEffect>of(), List.<String>of(),
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR),
                weatherProvenance);
    }

    private static PredictionResult resultWithNextStationAndDisruptionImpact(
            Station nextStation, com.railpredictor.model.domain.DisruptionImpactAssessment disruptionImpactAssessment) {
        return new PredictionResult(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, CURRENT_STATION, nextStation,
                5, 465.0, 919.0, 92.5, SectionType.NORMAL, 90.0, 8, 3, 10, 23,
                Instant.parse("2026-09-09T11:41:00Z"),
                new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of(), List.of()),
                List.<DisruptionResult>of(), List.<CascadeEffect>of(), List.<String>of(),
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR),
                null, disruptionImpactAssessment);
    }

    @Test
    void doesNothingWhenEvaluationIsDisabled() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(false), CLOCK);

        assertThatCode(() -> recorder.recordSafely(resultWithNextStation(NEXT_STATION))).doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    void doesNothingWhenNoRepositoryIsConfigured() {
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.empty(), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        assertThatCode(() -> recorder.recordSafely(resultWithNextStation(NEXT_STATION))).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenTheTrainHasNoNextStation() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStation(null));

        verify(repository, never()).save(any());
    }

    @Test
    void recordsASnapshotWhenEnabledWithARepositoryAndANextStation() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStation(NEXT_STATION));

        verify(repository).save(any(PredictionSnapshotEntity.class));
    }

    @Test
    void predictionMadeAtIsTheInjectedClocksInstantNeverSystemTimeOrDbInsertionTime() {
        // Phase 22 audit: predictionMadeAt must represent when the live prediction was actually
        // generated - proven here by using a clock deliberately far from wall-clock "now", so a
        // regression to System.currentTimeMillis()/Instant.now() (no clock) or a DB-assigned
        // timestamp would make this test fail loudly rather than passing by coincidence.
        Instant farPast = Instant.parse("2020-01-01T00:00:00Z");
        Clock distinctClock = Clock.fixed(farPast, ZoneOffset.UTC);
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), distinctClock);

        recorder.recordSafely(resultWithNextStation(NEXT_STATION));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPredictionMadeAt()).isEqualTo(farPast);
    }

    @Test
    void thePredictedNextStationDelayIsCurrentDelayPlusPredictedExtraDelayNotTheTotal() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStation(NEXT_STATION));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        // currentDelayMinutes=5, predictedExtraDelayMinutes=8 -> 13, NOT predictedTotalDelayMinutes=23.
        assertThat(captor.getValue().getPredictedNextStationDelayMinutes()).isEqualTo(13);
        assertThat(captor.getValue().getPredictedTotalDelayMinutes()).isEqualTo(23);
        assertThat(captor.getValue().getTargetStationCode()).isEqualTo("RTM");
        assertThat(captor.getValue().getEvaluationStatus()).isEqualTo("PENDING");
    }

    @Test
    void everyLiveRecordingIsStampedWithLiveEvaluationMode() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStation(NEXT_STATION));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEvaluationMode()).isEqualTo("LIVE_EVALUATION");
    }

    @Test
    void capturesTheResultsWeatherProvenanceWhenPresent() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStationAndWeatherProvenance(NEXT_STATION, "open-meteo"));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWeatherProvenance()).isEqualTo("open-meteo");
    }

    @Test
    void capturesNullWeatherProvenanceWhenWeatherWasUnavailable() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStationAndWeatherProvenance(NEXT_STATION, null));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWeatherProvenance()).isNull();
    }

    @Test
    void capturesTheResultsDisruptionImpactMinutesWhenEstimated() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        com.railpredictor.model.domain.DisruptionImpact impact = new com.railpredictor.model.domain.DisruptionImpact(
                com.railpredictor.model.domain.RailwayDisruptionType.ENGINEERING_BLOCK,
                com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 15, "heuristic", DataProvenance.MOCK);
        com.railpredictor.model.domain.DisruptionImpactAssessment assessment =
                new com.railpredictor.model.domain.DisruptionImpactAssessment(
                        com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 15, List.of(impact), false,
                        com.railpredictor.model.domain.CalibrationStatus.INSUFFICIENT_DATA);

        recorder.recordSafely(resultWithNextStationAndDisruptionImpact(NEXT_STATION, assessment));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDisruptionImpactMinutes()).isEqualTo(15);
    }

    @Test
    void capturesNullDisruptionImpactMinutesWhenDataWasUnavailable() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        recorder.recordSafely(resultWithNextStationAndDisruptionImpact(
                NEXT_STATION, com.railpredictor.model.domain.DisruptionImpactAssessment.unavailable()));

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDisruptionImpactMinutes()).isNull();
    }

    @Test
    void readsPredictedNextStationDelayDirectlyFromTheResultNeverRecomputingIt() {
        // Phase 21: the recorder must use result.predictedNextStationDelayMinutes() as-is (computed
        // once, authoritatively, by PredictionEngine) - never currentDelayMinutes +
        // predictedExtraDelayMinutes, which would silently drop the next-station historical/
        // disruption contribution.
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        PredictionResult result = new PredictionResult(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, CURRENT_STATION, NEXT_STATION,
                5, 465.0, 919.0, 92.5, SectionType.NORMAL, 90.0, 8, 3, 10, 23,
                Instant.parse("2026-09-09T11:41:00Z"),
                new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of(), List.of()),
                List.<DisruptionResult>of(), List.<CascadeEffect>of(), List.<String>of(),
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR),
                null,
                com.railpredictor.model.domain.DisruptionImpactAssessment.unavailable(),
                7,
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR),
                20); // deliberately NOT current(5) + extra(8) = 13, to prove it's read as-is

        recorder.recordSafely(result);

        org.mockito.ArgumentCaptor<PredictionSnapshotEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PredictionSnapshotEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPredictedNextStationDelayMinutes()).isEqualTo(20);
        assertThat(captor.getValue().getNextStationHistoricalAdjustmentMinutes()).isEqualTo(7);
        assertThat(captor.getValue().getNextStationHistoricalAdjustmentSource()).isEqualTo("SECTION");
        assertThat(captor.getValue().getNextStationHistoricalAdjustmentProvenance()).isEqualTo(DataProvenance.RAILRADAR);
        assertThat(captor.getValue().getPredictedExtraDelayMinutes()).isEqualTo(8);
    }

    @Test
    void aPersistenceFailureIsCaughtRatherThanPropagated() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        PredictionSnapshotRecorder recorder = new PredictionSnapshotRecorder(
                Optional.of(repository), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(true), CLOCK);

        assertThatCode(() -> recorder.recordSafely(resultWithNextStation(NEXT_STATION))).doesNotThrowAnyException();
    }
}
