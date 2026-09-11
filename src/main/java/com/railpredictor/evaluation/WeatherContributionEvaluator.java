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
 * Compares real prediction accuracy WITH vs. WITHOUT weather data (Phase 20, spec item 8) - a
 * paired comparison on one single population (every already-evaluated snapshot), partitioned by
 * whether {@code weatherProvenance} was recorded, never two separately-sourced populations. This
 * is the one weather ablation that's actually reconstructable from persisted snapshots: weather
 * only ever reaches a prediction indirectly, by triggering {@code HeavyRainModel}/
 * {@code DenseFogModel} inside simulation (see {@code SimulationContributionEvaluator} and
 * docs/prediction-model.md's Phase 20 notes) - {@code weatherProvenance} itself is the only
 * evidence a snapshot carries of whether weather was available to that run at all.
 */
@Component
public class WeatherContributionEvaluator {

    private static final String WITH_WEATHER = "WITH_WEATHER";
    private static final String WITHOUT_WEATHER = "WITHOUT_WEATHER";

    private final PredictionAccuracyReportBuilder reportBuilder;
    private final EvaluationCalibrationProperties properties;

    public WeatherContributionEvaluator(
            PredictionAccuracyReportBuilder reportBuilder, EvaluationCalibrationProperties properties) {
        this.reportBuilder = reportBuilder;
        this.properties = properties;
    }

    /** @param evaluatedSnapshots already-evaluated (EXACT/APPROXIMATE) snapshots only. */
    public Map<String, AblationVariantResult> evaluate(List<PredictionSnapshot> evaluatedSnapshots) {
        Objects.requireNonNull(evaluatedSnapshots, "evaluatedSnapshots");
        assertAlreadyEvaluated(evaluatedSnapshots);

        List<PredictionSnapshot> withWeather = evaluatedSnapshots.stream()
                .filter(s -> s.weatherProvenance() != null)
                .toList();
        List<PredictionSnapshot> withoutWeather = evaluatedSnapshots.stream()
                .filter(s -> s.weatherProvenance() == null)
                .toList();

        Map<String, AblationVariantResult> result = new LinkedHashMap<>();
        result.put(WITHOUT_WEATHER, variantResult(WITHOUT_WEATHER, withoutWeather));
        result.put(WITH_WEATHER, variantResult(WITH_WEATHER, withWeather));
        return result;
    }

    private AblationVariantResult variantResult(String name, List<PredictionSnapshot> subset) {
        PredictionAccuracySlice slice = sliceOf(subset);
        return new AblationVariantResult(name, slice, slice.sampleCount() >= properties.minimumSampleCount());
    }

    private PredictionAccuracySlice sliceOf(List<PredictionSnapshot> subset) {
        var report = reportBuilder.buildReport(subset);
        return report.overall();
    }

    private static void assertAlreadyEvaluated(List<PredictionSnapshot> snapshots) {
        boolean anyUnevaluated = snapshots.stream()
                .anyMatch(s -> s.evaluationStatus() != PredictionEvaluationStatus.EVALUATED_EXACT
                        && s.evaluationStatus() != PredictionEvaluationStatus.EVALUATED_APPROXIMATE);
        if (anyUnevaluated) {
            throw new IllegalArgumentException(
                    "WeatherContributionEvaluator requires already-evaluated snapshots only");
        }
    }
}
