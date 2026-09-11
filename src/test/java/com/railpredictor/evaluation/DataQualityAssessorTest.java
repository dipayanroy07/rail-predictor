package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataQualityAssessorTest {

    @Test
    void noRepositoryConfiguredYieldsEmptyReportWithExplicitNote() {
        DataQualityAssessor assessor = new DataQualityAssessor(Optional.empty(), mock(PredictionSnapshotEntityMapper.class));

        DataQualityReport report = assessor.assess();

        assertThat(report.totalSnapshots()).isZero();
        assertThat(report.pointInTimeReproducibilityNote()).containsIgnoringCase("no snapshot database");
    }

    @Test
    void repositoryConfiguredButEmptyYieldsEmptyReportWithADifferentNote() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        DataQualityAssessor assessor = new DataQualityAssessor(Optional.of(repository), mock(PredictionSnapshotEntityMapper.class));

        DataQualityReport report = assessor.assess();

        assertThat(report.totalSnapshots()).isZero();
        assertThat(report.pointInTimeReproducibilityNote()).containsIgnoringCase("zero snapshots");
    }

    @Test
    void realSnapshotsProduceGenuineCounts() {
        PredictionSnapshot exactWithWeatherAndDisruption = evaluated(
                PredictionEvaluationStatus.EVALUATED_EXACT, "open-meteo", 5, HistoricalAdjustmentSource.SECTION, 8, 5);
        PredictionSnapshot approximateNoWeatherNoDisruption = evaluated(
                PredictionEvaluationStatus.EVALUATED_APPROXIMATE, null, null, HistoricalAdjustmentSource.NONE, 5, 5);
        PredictionSnapshot pending = new PredictionSnapshot(
                3L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null);

        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotEntity entity1 = mock(PredictionSnapshotEntity.class);
        PredictionSnapshotEntity entity2 = mock(PredictionSnapshotEntity.class);
        PredictionSnapshotEntity entity3 = mock(PredictionSnapshotEntity.class);
        when(repository.findAll()).thenReturn(List.of(entity1, entity2, entity3));

        PredictionSnapshotEntityMapper mapper = mock(PredictionSnapshotEntityMapper.class);
        when(mapper.toDomain(entity1)).thenReturn(exactWithWeatherAndDisruption);
        when(mapper.toDomain(entity2)).thenReturn(approximateNoWeatherNoDisruption);
        when(mapper.toDomain(entity3)).thenReturn(pending);

        DataQualityAssessor assessor = new DataQualityAssessor(Optional.of(repository), mapper);

        DataQualityReport report = assessor.assess();

        assertThat(report.totalSnapshots()).isEqualTo(3);
        assertThat(report.evaluatedSnapshots()).isEqualTo(2);
        assertThat(report.exactEvaluations()).isEqualTo(1);
        assertThat(report.approximateEvaluations()).isEqualTo(1);
        assertThat(report.pendingSnapshots()).isEqualTo(1);
        assertThat(report.weatherAvailableCount()).isEqualTo(1);
        assertThat(report.historicalAvailableCount()).isEqualTo(1);
        assertThat(report.disruptionImpactAvailableCount()).isEqualTo(1);
        assertThat(report.simulationContributedCount()).isEqualTo(1); // exact one: predicted 8 - current 5 = 3 > 0
        assertThat(report.distinctTrainCount()).isEqualTo(1);
        assertThat(report.distinctStationCount()).isEqualTo(1);
    }

    private static PredictionSnapshot evaluated(
            PredictionEvaluationStatus status, String weatherProvenance, Integer disruptionImpactMinutes,
            HistoricalAdjustmentSource historicalSource, int predicted, int actual) {
        return new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, predicted, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, historicalSource, DataProvenance.RAILRADAR,
                75.0, status, actual, predicted - actual, Instant.parse("2026-09-09T11:00:00Z"),
                PredictionEvaluationMode.LIVE_EVALUATION, weatherProvenance, disruptionImpactMinutes);
    }
}
