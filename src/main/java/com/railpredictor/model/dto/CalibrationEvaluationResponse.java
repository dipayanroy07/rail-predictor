package com.railpredictor.model.dto;

import com.railpredictor.model.domain.CalibrationStatus;
import java.util.Map;

/**
 * JSON shape of {@code CalibrationEvaluationReport} (Phase 20) - present in
 * {@code GET /api/v1/evaluation/accuracy}'s {@code calibrationEvaluation} field only when
 * {@code evaluation.calibration.enabled=true}; {@code null} otherwise (mirrors
 * {@code AccuracyReportResponse.evaluationEnabled}'s own explicit-disabled-state pattern - never a
 * fabricated empty report).
 */
public record CalibrationEvaluationResponse(
        DataQualityResponse dataQuality,
        Map<String, AblationVariantResponse> byWeatherAvailability,
        Map<String, AblationVariantResponse> bySimulationContribution,
        ConfidenceCalibrationResponse confidenceAnalysis,
        CalibrationAssessmentResponse historicalWeightCalibration,
        CalibrationAssessmentResponse disruptionImpactCalibration,
        CalibrationStatus overallCalibrationStatus) {
}
