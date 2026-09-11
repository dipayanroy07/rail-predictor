package com.railpredictor.evaluation;

import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationEvaluationReport;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Assembles the top-level Phase 20 {@link CalibrationEvaluationReport} from already-fetched
 * snapshots - pure composition, no persistence access of its own (that's
 * {@link CalibrationEvaluationService}'s job, mirroring how {@link PredictionAccuracyReportBuilder}
 * is pure while {@link PredictionAccuracyReportService} owns the repository).
 */
@Component
public class CalibrationEvaluationReportBuilder {

    private final WeatherContributionEvaluator weatherEvaluator;
    private final SimulationContributionEvaluator simulationEvaluator;
    private final ConfidenceCalibrationAuditor confidenceAuditor;
    private final HistoricalWeightCalibrationAssessor historicalWeightAssessor;
    private final DisruptionImpactCalibrationAssessor disruptionImpactAssessor;

    public CalibrationEvaluationReportBuilder(
            WeatherContributionEvaluator weatherEvaluator,
            SimulationContributionEvaluator simulationEvaluator,
            ConfidenceCalibrationAuditor confidenceAuditor,
            HistoricalWeightCalibrationAssessor historicalWeightAssessor,
            DisruptionImpactCalibrationAssessor disruptionImpactAssessor) {
        this.weatherEvaluator = weatherEvaluator;
        this.simulationEvaluator = simulationEvaluator;
        this.confidenceAuditor = confidenceAuditor;
        this.historicalWeightAssessor = historicalWeightAssessor;
        this.disruptionImpactAssessor = disruptionImpactAssessor;
    }

    /**
     * @param allSnapshots every snapshot (any evaluation status) - only used for
     *                     {@code dataQuality}, which must report on pending/unevaluable snapshots
     *                     too, not just evaluated ones.
     * @param dataQuality  built by {@link DataQualityAssessor} - passed in rather than recomputed
     *                     here since it already required its own repository read.
     */
    public CalibrationEvaluationReport build(List<PredictionSnapshot> allSnapshots, DataQualityReport dataQuality) {
        Objects.requireNonNull(allSnapshots, "allSnapshots");
        Objects.requireNonNull(dataQuality, "dataQuality");

        List<PredictionSnapshot> evaluated = allSnapshots.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT
                        || s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_APPROXIMATE)
                .filter(s -> !s.quarantined())
                .toList();

        CalibrationAssessment historicalWeightCalibration = historicalWeightAssessor.assess(evaluated);
        CalibrationAssessment disruptionImpactCalibration = disruptionImpactAssessor.assess(evaluated);

        CalibrationStatus overallCalibrationStatus = leastConfidentOf(
                historicalWeightCalibration.status(), disruptionImpactCalibration.status());

        return new CalibrationEvaluationReport(
                dataQuality,
                weatherEvaluator.evaluate(evaluated),
                simulationEvaluator.evaluate(evaluated),
                confidenceAuditor.audit(evaluated),
                historicalWeightCalibration,
                disruptionImpactCalibration,
                overallCalibrationStatus);
    }

    /**
     * The system as a whole can only be as confident as its least-confident individual
     * calibration attempt - one {@code VALIDATED} parameter never excuses reporting the overall
     * status as anything better than another parameter's {@code INSUFFICIENT_DATA}.
     */
    private static CalibrationStatus leastConfidentOf(CalibrationStatus a, CalibrationStatus b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
