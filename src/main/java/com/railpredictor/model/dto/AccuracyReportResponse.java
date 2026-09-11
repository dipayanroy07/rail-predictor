package com.railpredictor.model.dto;

import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import java.util.Map;

/**
 * The `GET /api/v1/evaluation/accuracy` response body (Phase 16H-6).
 *
 * <p>{@code evaluationEnabled=false} is an explicit, predictable state - prediction-accuracy
 * tracking is turned off ({@code prediction.evaluation.enabled=false}, the default), so every
 * other field is {@code null}. This is deliberately different from an <em>enabled</em> report with
 * zero evaluated samples (a normal, valid outcome: {@code overall.sampleCount() == 0}, every other
 * field still populated) - a client must be able to tell "nothing to report yet" apart from
 * "this feature isn't turned on", never mistake one for the other, and never be shown a fabricated
 * empty dataset instead of the true disabled state.
 *
 * <p>{@code calibrationEvaluation} (Phase 20) is {@code null} whenever {@code
 * evaluation.calibration.enabled=false} (the default) - independent of {@code evaluationEnabled}
 * itself, since the Phase 20 empirical-calibration framework is a separate opt-in from Phase
 * 16H-6's accuracy tracking.
 */
public record AccuracyReportResponse(
        boolean evaluationEnabled,
        AccuracySliceResponse overall,
        AccuracyComparisonResponse exactOnly,
        Map<HistoricalAdjustmentSource, AccuracySliceResponse> bySource,
        Map<String, AccuracySliceResponse> byProvenance,
        Map<PredictionEvaluationMode, AccuracySliceResponse> byEvaluationMode,
        CalibrationEvaluationResponse calibrationEvaluation) {

    /** Pre-Phase-20 shape, preserved for any existing caller - {@code calibrationEvaluation} defaults to
     * {@code null} (equivalent to {@code evaluation.calibration.enabled=false}). */
    public AccuracyReportResponse(
            boolean evaluationEnabled,
            AccuracySliceResponse overall,
            AccuracyComparisonResponse exactOnly,
            Map<HistoricalAdjustmentSource, AccuracySliceResponse> bySource,
            Map<String, AccuracySliceResponse> byProvenance,
            Map<PredictionEvaluationMode, AccuracySliceResponse> byEvaluationMode) {
        this(evaluationEnabled, overall, exactOnly, bySource, byProvenance, byEvaluationMode, null);
    }
}
