package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.exception.InvalidAccuracyFilterException;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracyReportFilter;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PredictionAccuracyReportServiceTest {

    private final PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
    private final PredictionAccuracyReportBuilder reportBuilder =
            new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator());
    private final PredictionSnapshotEntityMapper entityMapper = new PredictionSnapshotEntityMapper();

    private static PredictionSnapshotEntity evaluatedEntity(String trainNumber, String stationCode, Instant predictionMadeAt) {
        PredictionSnapshotEntity entity = new PredictionSnapshotEntity(
                trainNumber, predictionMadeAt, stationCode,
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK.name(), DataProvenance.RAILRADAR, 75.0,
                PredictionEvaluationStatus.PENDING.name(), null, null, null);
        entity.applyEvaluation(PredictionEvaluationStatus.EVALUATED_EXACT.name(), 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
        return entity;
    }

    @Test
    void returnsEmptyWhenEvaluationIsDisabled() {
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(false));

        Optional<PredictionAccuracyReport> report = service.getReport(PredictionAccuracyReportFilter.none());

        assertThat(report).isEmpty();
    }

    @Test
    void returnsAnAllZeroReportWhenEnabledButNoRepositoryIsConfigured() {
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.empty(), entityMapper, reportBuilder, new PredictionEvaluationProperties(true));

        Optional<PredictionAccuracyReport> report = service.getReport(PredictionAccuracyReportFilter.none());

        assertThat(report).isPresent();
        assertThat(report.get().overall().sampleCount()).isZero();
    }

    @Test
    void returnsAnAllZeroReportWhenEnabledButNothingHasBeenEvaluatedYet() {
        when(repository.findByEvaluationStatusIn(List.of("EVALUATED_EXACT", "EVALUATED_APPROXIMATE")))
                .thenReturn(List.of());
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(true));

        Optional<PredictionAccuracyReport> report = service.getReport(PredictionAccuracyReportFilter.none());

        assertThat(report).isPresent();
        assertThat(report.get().overall().sampleCount()).isZero();
    }

    @Test
    void appliesTheTrainNumberFilter() {
        when(repository.findByEvaluationStatusIn(List.of("EVALUATED_EXACT", "EVALUATED_APPROXIMATE")))
                .thenReturn(List.of(
                        evaluatedEntity("12952", "KOTA", Instant.parse("2026-09-09T10:00:00Z")),
                        evaluatedEntity("99999", "KOTA", Instant.parse("2026-09-09T10:00:00Z"))));
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(true));

        PredictionAccuracyReportFilter filter =
                new PredictionAccuracyReportFilter("12952", null, null, null, null, null);
        Optional<PredictionAccuracyReport> report = service.getReport(filter);

        assertThat(report.get().overall().sampleCount()).isEqualTo(1);
    }

    @Test
    void appliesTheTimeRangeFilter() {
        when(repository.findByEvaluationStatusIn(List.of("EVALUATED_EXACT", "EVALUATED_APPROXIMATE")))
                .thenReturn(List.of(
                        evaluatedEntity("12952", "KOTA", Instant.parse("2026-01-01T00:00:00Z")),
                        evaluatedEntity("12952", "KOTA", Instant.parse("2026-06-01T00:00:00Z"))));
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(true));

        PredictionAccuracyReportFilter filter = new PredictionAccuracyReportFilter(
                null, null, Instant.parse("2026-03-01T00:00:00Z"), null, null, null);
        Optional<PredictionAccuracyReport> report = service.getReport(filter);

        assertThat(report.get().overall().sampleCount()).isEqualTo(1);
    }

    @Test
    void invertedTimeRangeThrowsInvalidAccuracyFilterException() {
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(true));

        PredictionAccuracyReportFilter filter = new PredictionAccuracyReportFilter(
                null, null, Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), null, null);

        assertThatThrownBy(() -> service.getReport(filter))
                .isInstanceOf(InvalidAccuracyFilterException.class);
    }

    @Test
    void excludesQuarantinedSnapshotsFromTheReportWhileStillReadingThemFromTheRepository() {
        PredictionSnapshotEntity valid = evaluatedEntity("12952", "KOTA", Instant.parse("2026-09-09T10:00:00Z"));
        PredictionSnapshotEntity quarantinedEntity = evaluatedEntity("12952", "KOTA", Instant.parse("2026-09-09T10:00:00Z"));
        quarantinedEntity.applyQuarantine("matched against a RailRadar upcoming-stop placeholder (Phase 22D/22C)");
        when(repository.findByEvaluationStatusIn(List.of("EVALUATED_EXACT", "EVALUATED_APPROXIMATE")))
                .thenReturn(List.of(valid, quarantinedEntity));
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(true));

        Optional<PredictionAccuracyReport> report = service.getReport(PredictionAccuracyReportFilter.none());

        assertThat(report.get().overall().sampleCount()).isEqualTo(1);
    }

    @Test
    void doesNotValidateTheFilterWhenEvaluationIsDisabled() {
        PredictionAccuracyReportService service = new PredictionAccuracyReportService(
                Optional.of(repository), entityMapper, reportBuilder, new PredictionEvaluationProperties(false));

        PredictionAccuracyReportFilter invertedRange = new PredictionAccuracyReportFilter(
                null, null, Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), null, null);

        assertThat(service.getReport(invertedRange)).isEmpty();
    }
}
