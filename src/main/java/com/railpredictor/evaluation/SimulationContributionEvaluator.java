package com.railpredictor.evaluation;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.AblationVariantResult;
import com.railpredictor.model.domain.PredictionAccuracySlice;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Compares real prediction accuracy WITH vs. WITHOUT a simulation contribution (Phase 20, spec
 * item 10) - a paired comparison on one single population, partitioned by
 * {@code predictedExtraDelayMinutes}, simulation's own directly-persisted raw contribution (Phase
 * 21 - previously reconstructed as {@code predictedNextStationDelayMinutes - currentDelayMinutes},
 * which became ambiguous once historical adjustment/disruption impact also started contributing to
 * {@code predictedNextStationDelayMinutes}; see docs/prediction-model.md's Phase 21 notes). No
 * individual one of the six simulated disruption models can be isolated this way (a snapshot
 * records only the aggregate contribution, not which model(s) fired).
 */
@Component
public class SimulationContributionEvaluator {

    private static final String WITH_SIMULATION_CONTRIBUTION = "WITH_SIMULATION_CONTRIBUTION";
    private static final String WITHOUT_SIMULATION_CONTRIBUTION = "WITHOUT_SIMULATION_CONTRIBUTION";

    private final PredictionAccuracyReportBuilder reportBuilder;
    private final EvaluationCalibrationProperties properties;

    public SimulationContributionEvaluator(
            PredictionAccuracyReportBuilder reportBuilder, EvaluationCalibrationProperties properties) {
        this.reportBuilder = reportBuilder;
        this.properties = properties;
    }

    /** @param evaluatedSnapshots already-evaluated (EXACT/APPROXIMATE) snapshots only. */
    public Map<String, AblationVariantResult> evaluate(List<PredictionSnapshot> evaluatedSnapshots) {
        Objects.requireNonNull(evaluatedSnapshots, "evaluatedSnapshots");
        assertAlreadyEvaluated(evaluatedSnapshots);

        List<PredictionSnapshot> withContribution = evaluatedSnapshots.stream()
                .filter(s -> s.predictedExtraDelayMinutes() > 0)
                .toList();
        List<PredictionSnapshot> withoutContribution = evaluatedSnapshots.stream()
                .filter(s -> s.predictedExtraDelayMinutes() <= 0)
                .toList();

        Map<String, AblationVariantResult> result = new LinkedHashMap<>();
        result.put(WITHOUT_SIMULATION_CONTRIBUTION, variantResult(WITHOUT_SIMULATION_CONTRIBUTION, withoutContribution));
        result.put(WITH_SIMULATION_CONTRIBUTION, variantResult(WITH_SIMULATION_CONTRIBUTION, withContribution));
        return result;
    }

    private AblationVariantResult variantResult(String name, List<PredictionSnapshot> subset) {
        PredictionAccuracySlice slice = reportBuilder.buildReport(subset).overall();
        return new AblationVariantResult(name, slice, slice.sampleCount() >= properties.minimumSampleCount());
    }

    private static void assertAlreadyEvaluated(List<PredictionSnapshot> snapshots) {
        boolean anyUnevaluated = snapshots.stream()
                .anyMatch(s -> s.evaluationStatus() != PredictionEvaluationStatus.EVALUATED_EXACT
                        && s.evaluationStatus() != PredictionEvaluationStatus.EVALUATED_APPROXIMATE);
        if (anyUnevaluated) {
            throw new IllegalArgumentException(
                    "SimulationContributionEvaluator requires already-evaluated snapshots only");
        }
    }
}
