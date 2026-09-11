package com.railpredictor.evaluation;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.CalibrationEvaluationReport;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The read side of Phase 20: turns the current, real {@code prediction_snapshots} table into a
 * {@link CalibrationEvaluationReport}, gated behind {@code evaluation.calibration.enabled}
 * (default {@code false}) exactly like {@code PredictionAccuracyReportService} is gated behind
 * {@code prediction.evaluation.enabled} - never auto-enabled, never computed on startup.
 */
@Service
public class CalibrationEvaluationService {

    private final Optional<PredictionSnapshotRepository> repository;
    private final PredictionSnapshotEntityMapper entityMapper;
    private final DataQualityAssessor dataQualityAssessor;
    private final CalibrationEvaluationReportBuilder reportBuilder;
    private final EvaluationCalibrationProperties properties;

    public CalibrationEvaluationService(
            Optional<PredictionSnapshotRepository> repository,
            PredictionSnapshotEntityMapper entityMapper,
            DataQualityAssessor dataQualityAssessor,
            CalibrationEvaluationReportBuilder reportBuilder,
            EvaluationCalibrationProperties properties) {
        this.repository = repository;
        this.entityMapper = entityMapper;
        this.dataQualityAssessor = dataQualityAssessor;
        this.reportBuilder = reportBuilder;
        this.properties = properties;
    }

    /** @return {@code Optional.empty()} when {@code evaluation.calibration.enabled=false} (the default). */
    public Optional<CalibrationEvaluationReport> getReport() {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        DataQualityReport dataQuality = dataQualityAssessor.assess();
        List<PredictionSnapshot> allSnapshots = repository
                .map(PredictionSnapshotRepository::findAll)
                .orElseGet(List::of)
                .stream()
                .map(entityMapper::toDomain)
                .toList();

        return Optional.of(reportBuilder.build(allSnapshots, dataQuality));
    }
}
