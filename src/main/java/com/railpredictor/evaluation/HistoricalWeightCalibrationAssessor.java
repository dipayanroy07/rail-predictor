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
 * spec item 7; corrected in Phase 21) against real evaluated outcomes.
 *
 * <p><b>Phase 21 correction:</b> before this phase, {@code PredictionSnapshot.
 * predictedNextStationDelayMinutes} - the only quantity this codebase evaluates for accuracy - was
 * computed purely as {@code currentDelayMinutes + predictedExtraDelayMinutes}, so no historical
 * signal could ever move the evaluated error; this assessor always reported
 * {@code CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED}. Phase 21 added
 * {@code nextStationHistoricalAdjustmentMinutes} - a properly next-station-scoped historical
 * signal (the immediate next section's own historical delay-change, or the equally next-station-
 * scoped station-level fallback - never the whole-remaining-route sum {@code
 * historicalAdjustmentMinutes} uses) - which now genuinely contributes to
 * {@code predictedNextStationDelayMinutes}, using the exact same {@code
 * prediction.historical-adjustment.weight} this assessor is evaluating. The metric is therefore no
 * longer structurally blocked: this assessor now reports {@code INSUFFICIENT_SAMPLE_SIZE} (or
 * {@code MOCK_DATA_ONLY}) instead, whenever real data is insufficient - never
 * {@code METRIC_STRUCTURALLY_UNAFFECTED} again for this parameter.
 *
 * <p><b>This phase still does not implement an actual weight-search/selection algorithm</b> - even
 * once {@code evaluatedSnapshots.size()} meets {@code evaluation.calibration.minimum-sample-count},
 * this assessor reports {@code INSUFFICIENT_DATA} rather than fabricating a
 * {@code PROVISIONALLY_CALIBRATED}/{@code VALIDATED} result no real search has actually produced -
 * building that search is explicitly left to a future phase (see docs/prediction-model.md's Phase
 * 21 notes and this codebase's own "do not calibrate anything unless a real algorithm selected and
 * validated a candidate" rule). The chronological train/validation split
 * ({@link ChronologicalSplitter}) is still performed and its sample counts still reported, so this
 * assessment is transparent about how much real, point-in-time-ordered evidence already exists.
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

        int realSampleCount = evaluatedSnapshots.size();
        int minimumSampleCount = calibrationProperties.minimumSampleCount();

        if (realSampleCount < minimumSampleCount) {
            String explanation = "Only " + realSampleCount + " real evaluated snapshot(s) exist - "
                    + "evaluation.calibration.minimum-sample-count=" + minimumSampleCount
                    + " requires more before a chronological train/validation split is even attempted. "
                    + "nextStationHistoricalAdjustmentMinutes now genuinely contributes to the evaluated "
                    + "predictedNextStationDelayMinutes (Phase 21) - this is a real data-volume gap, not a "
                    + "structural one.";
            return CalibrationAssessment.blocked(
                    PARAMETER_NAME, CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE, explanation, realSampleCount);
        }

        ChronologicalSplitter.Split split = splitter.split(evaluatedSnapshots, calibrationProperties.validationSplit());

        String explanation = "Enough real evaluated snapshots exist to perform a chronological "
                + "train/validation split (" + split.training().size() + " training / "
                + split.validation().size() + " validation), and nextStationHistoricalAdjustmentMinutes "
                + "now genuinely contributes to the evaluated predictedNextStationDelayMinutes (Phase 21) - "
                + "but this codebase does not yet implement a candidate-weight search/selection algorithm. "
                + "Reporting INSUFFICIENT_DATA rather than fabricating a validated coefficient no real "
                + "search has produced; building that search is the recommended next phase.";

        return new CalibrationAssessment(
                PARAMETER_NAME,
                CalibrationStatus.INSUFFICIENT_DATA,
                CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE,
                explanation,
                realSampleCount,
                split.training().size(),
                split.validation().size(),
                historicalAdjustmentProperties.weight(),
                null,
                null);
    }
}
