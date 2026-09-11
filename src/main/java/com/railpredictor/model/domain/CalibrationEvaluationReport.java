package com.railpredictor.model.domain;

import java.util.Map;

/**
 * Phase 20's top-level empirical-evaluation output: everything needed to answer "which components
 * actually improve prediction accuracy, and how confident can we be" - built entirely from real
 * {@code PredictionSnapshot} data (never mock data, never synthetic test fixtures), gated behind
 * {@code evaluation.calibration.enabled} (default {@code false}).
 *
 * <p>Deliberately a separate object from {@link PredictionAccuracyReport} (Phase 16H-6) rather than
 * an extension of it - this is a distinct, heavier analysis (data quality, subgroup ablation,
 * confidence audit, calibration attempts), and keeping it separate means the existing, heavily
 * tested Phase 16H-6 report contract needed no changes at all.
 *
 * <p>{@code byWeatherAvailability}/{@code bySimulationContribution} are the only two subgroup
 * breakdowns this phase builds against real prediction-accuracy improvement (not just audit
 * counts) - see docs/prediction-model.md's Phase 20 notes for exactly why
 * {@code historicalAdjustmentSource}/disruption-impact breakdowns are deliberately not built the
 * same way (the evaluated quantity structurally cannot reflect either term).
 */
public record CalibrationEvaluationReport(
        DataQualityReport dataQuality,
        Map<String, AblationVariantResult> byWeatherAvailability,
        Map<String, AblationVariantResult> bySimulationContribution,
        ConfidenceCalibrationReport confidenceAnalysis,
        CalibrationAssessment historicalWeightCalibration,
        CalibrationAssessment disruptionImpactCalibration,
        CalibrationStatus overallCalibrationStatus) {

    public CalibrationEvaluationReport {
        dataQuality = Guard.requireNonNull(dataQuality, "dataQuality");
        byWeatherAvailability = Map.copyOf(Guard.requireNonNull(byWeatherAvailability, "byWeatherAvailability"));
        bySimulationContribution = Map.copyOf(Guard.requireNonNull(bySimulationContribution, "bySimulationContribution"));
        confidenceAnalysis = Guard.requireNonNull(confidenceAnalysis, "confidenceAnalysis");
        historicalWeightCalibration = Guard.requireNonNull(historicalWeightCalibration, "historicalWeightCalibration");
        disruptionImpactCalibration = Guard.requireNonNull(disruptionImpactCalibration, "disruptionImpactCalibration");
        overallCalibrationStatus = Guard.requireNonNull(overallCalibrationStatus, "overallCalibrationStatus");
    }
}
