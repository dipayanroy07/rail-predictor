package com.railpredictor.evaluation;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Attempts to empirically calibrate {@code prediction.historical-adjustment.weight} (Phase 20,
 * spec item 7) against real evaluated outcomes.
 *
 * <p><b>Why this always reports {@code METRIC_STRUCTURALLY_UNAFFECTED}, regardless of sample
 * size:</b> {@code PredictionSnapshot.predictedNextStationDelayMinutes} - the only quantity this
 * codebase evaluates for accuracy - is computed purely as {@code currentDelayMinutes +
 * predictedExtraDelayMinutes} (see {@code PredictionEngine}/{@code PredictionSnapshotRecorder}).
 * {@code historicalAdjustmentMinutes} only ever feeds the separate, destination-scoped
 * {@code predictedTotalDelayMinutes}, which no evaluation in this codebase compares against an
 * actual outcome. Changing {@code prediction.historical-adjustment.weight} therefore cannot move
 * the evaluated error by construction - no amount of real data changes this conclusion, so this is
 * reported as a structural blocker, not an {@code INSUFFICIENT_SAMPLE_SIZE} data problem (which
 * would incorrectly imply that collecting more snapshots could eventually unblock calibration).
 *
 * <p>The chronological train/validation split ({@link ChronologicalSplitter}) is still performed
 * and its sample counts still reported, purely so this assessment is transparent about how much
 * real, point-in-time-ordered evidence exists - not because the split result changes the
 * conclusion.
 */
@Component
public class HistoricalWeightCalibrationAssessor {

    private static final String PARAMETER_NAME = "prediction.historical-adjustment.weight";

    private final ChronologicalSplitter splitter;
    private final HistoricalAdjustmentProperties historicalAdjustmentProperties;
    private final EvaluationCalibrationProperties calibrationProperties;

    public HistoricalWeightCalibrationAssessor(
            ChronologicalSplitter splitter,
            HistoricalAdjustmentProperties historicalAdjustmentProperties,
            EvaluationCalibrationProperties calibrationProperties) {
        this.splitter = splitter;
        this.historicalAdjustmentProperties = historicalAdjustmentProperties;
        this.calibrationProperties = calibrationProperties;
    }

    /** @param evaluatedSnapshots already-evaluated (EXACT/APPROXIMATE) snapshots only. */
    public CalibrationAssessment assess(List<PredictionSnapshot> evaluatedSnapshots) {
        Objects.requireNonNull(evaluatedSnapshots, "evaluatedSnapshots");

        String explanation = "predictedNextStationDelayMinutes (the only quantity this codebase evaluates) is "
                + "computed as currentDelayMinutes + predictedExtraDelayMinutes only - historicalAdjustmentMinutes "
                + "never contributes to it, only to the separate, unevaluated predictedTotalDelayMinutes. "
                + "Calibrating prediction.historical-adjustment.weight against the evaluated metric is therefore "
                + "structurally impossible with the current evaluation scope, regardless of sample size.";

        if (evaluatedSnapshots.size() < 2) {
            return CalibrationAssessment.blocked(
                    PARAMETER_NAME, CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED, explanation,
                    evaluatedSnapshots.size());
        }

        ChronologicalSplitter.Split split = splitter.split(evaluatedSnapshots, calibrationProperties.validationSplit());

        return new CalibrationAssessment(
                PARAMETER_NAME,
                CalibrationStatus.INSUFFICIENT_DATA,
                CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED,
                explanation,
                evaluatedSnapshots.size(),
                split.training().size(),
                split.validation().size(),
                historicalAdjustmentProperties.weight(),
                null,
                null);
    }
}
