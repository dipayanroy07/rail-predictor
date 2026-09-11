package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.CalibrationEvaluationReport;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalibrationEvaluationServiceTest {

    @Test
    void returnsEmptyWhenCalibrationIsDisabledAndNeverTouchesTheRepository() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        DataQualityAssessor dataQualityAssessor = mock(DataQualityAssessor.class);
        CalibrationEvaluationReportBuilder reportBuilder = mock(CalibrationEvaluationReportBuilder.class);
        EvaluationCalibrationProperties properties = new EvaluationCalibrationProperties(false, 30, 0.3, 5.0);

        CalibrationEvaluationService service = new CalibrationEvaluationService(
                Optional.of(repository), mock(PredictionSnapshotEntityMapper.class), dataQualityAssessor, reportBuilder, properties);

        Optional<CalibrationEvaluationReport> result = service.getReport();

        assertThat(result).isEmpty();
        verify(repository, never()).findAll();
        verify(dataQualityAssessor, never()).assess();
    }

    @Test
    void buildsAReportWhenEnabled() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        DataQualityAssessor dataQualityAssessor = mock(DataQualityAssessor.class);
        DataQualityReport dataQuality = DataQualityReport.empty("note");
        when(dataQualityAssessor.assess()).thenReturn(dataQuality);
        CalibrationEvaluationReportBuilder reportBuilder = mock(CalibrationEvaluationReportBuilder.class);
        CalibrationEvaluationReport expected = mock(CalibrationEvaluationReport.class);
        when(reportBuilder.build(List.of(), dataQuality)).thenReturn(expected);
        EvaluationCalibrationProperties properties = new EvaluationCalibrationProperties(true, 30, 0.3, 5.0);

        CalibrationEvaluationService service = new CalibrationEvaluationService(
                Optional.of(repository), mock(PredictionSnapshotEntityMapper.class), dataQualityAssessor, reportBuilder, properties);

        Optional<CalibrationEvaluationReport> result = service.getReport();

        assertThat(result).contains(expected);
    }

    @Test
    void withNoRepositoryConfiguredStillBuildsAReportFromDataQualityAssessorsOwnHandling() {
        DataQualityAssessor dataQualityAssessor = mock(DataQualityAssessor.class);
        DataQualityReport dataQuality = DataQualityReport.empty("no database configured");
        when(dataQualityAssessor.assess()).thenReturn(dataQuality);
        CalibrationEvaluationReportBuilder reportBuilder = mock(CalibrationEvaluationReportBuilder.class);
        CalibrationEvaluationReport expected = mock(CalibrationEvaluationReport.class);
        when(reportBuilder.build(List.of(), dataQuality)).thenReturn(expected);
        EvaluationCalibrationProperties properties = new EvaluationCalibrationProperties(true, 30, 0.3, 5.0);

        CalibrationEvaluationService service = new CalibrationEvaluationService(
                Optional.empty(), mock(PredictionSnapshotEntityMapper.class), dataQualityAssessor, reportBuilder, properties);

        Optional<CalibrationEvaluationReport> result = service.getReport();

        assertThat(result).contains(expected);
    }
}
