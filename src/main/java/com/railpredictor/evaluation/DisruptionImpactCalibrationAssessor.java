package com.railpredictor.evaluation;

import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Attempts to empirically calibrate the Phase 19 {@code HeuristicDisruptionImpactPolicy}'s default
 * per-type delay minutes (spec item 9; corrected in Phase 21) against real evaluated outcomes.
 *
 * <p><b>Phase 21 correction:</b> {@code disruptionImpactMinutes} was <em>already</em> correctly
 * next-section-scoped before this phase - {@code DisruptionImpactAggregator} is only ever given the
 * train's current section (current station → next station), never the whole remaining route (see
 * {@code PredictionService}). The reason this assessor previously reported
 * {@code METRIC_STRUCTURALLY_UNAFFECTED} was purely a wiring gap: {@code
 * PredictionSnapshot.predictedNextStationDelayMinutes} never included {@code
 * disruptionImpactMinutes} at all before Phase 21 added it to {@code PredictionEngine}'s
 * next-station formula. Now that the wiring is fixed, this parameter is no longer structurally
 * blocked - this assessor reports {@code INSUFFICIENT_SAMPLE_SIZE} instead, whenever real data is
 * insufficient.
 *
 * <p><b>An independent limitation remains, unresolved by Phase 21</b>: {@link PredictionSnapshot}
 * still persists no field distinguishing whether an active disruption came from the real
 * RailRadar-backed provider or {@code MockRailwayDisruptionProvider} (spec item 9's "mock
 * disruptions must never be treated as empirical calibration evidence" requirement). Until that
 * gap is closed, this assessor cannot mechanically guarantee mock-data exclusion even once enough
 * samples exist - so it remains conservative (never claims {@code PROVISIONALLY_CALIBRATED}/
 * {@code VALIDATED}) regardless of sample count, and this gap is called out explicitly rather than
 * silently assumed away.
 */
@Component
public class DisruptionImpactCalibrationAssessor {

    private static final String PARAMETER_NAME =
            "railway-disruption-impact.* (HeuristicDisruptionImpactPolicy's per-type default delay minutes)";

    private final EvaluationCalibrationProperties calibrationProperties;

    public DisruptionImpactCalibrationAssessor(EvaluationCalibrationProperties calibrationProperties) {
        this.calibrationProperties = calibrationProperties;
    }

    /** @param evaluatedSnapshots already-evaluated (EXACT/APPROXIMATE) snapshots only. */
    public CalibrationAssessment assess(List<PredictionSnapshot> evaluatedSnapshots) {
        Objects.requireNonNull(evaluatedSnapshots, "evaluatedSnapshots");

        int realSampleCount = evaluatedSnapshots.size();
        int withDisruptionImpact = (int) evaluatedSnapshots.stream()
                .filter(s -> s.disruptionImpactMinutes() != null)
                .count();
        int minimumSampleCount = calibrationProperties.minimumSampleCount();

        if (realSampleCount < minimumSampleCount) {
            String explanation = "Only " + realSampleCount + " real evaluated snapshot(s) exist ("
                    + withDisruptionImpact + " with a recorded disruption impact) - "
                    + "evaluation.calibration.minimum-sample-count=" + minimumSampleCount + " requires more. "
                    + "disruptionImpactMinutes now genuinely contributes to the evaluated "
                    + "predictedNextStationDelayMinutes (Phase 21) - this is a real data-volume gap, not a "
                    + "structural one. A separate, unresolved gap: no field on PredictionSnapshot distinguishes "
                    + "real from mock disruption provenance, so mock-data exclusion could not yet be "
                    + "mechanically enforced even once enough samples exist.";
            return CalibrationAssessment.blocked(
                    PARAMETER_NAME, CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE, explanation, realSampleCount);
        }

        String explanation = "Enough real evaluated snapshots exist (" + realSampleCount + ", "
                + withDisruptionImpact + " with a recorded disruption impact), and disruptionImpactMinutes now "
                + "genuinely contributes to the evaluated predictedNextStationDelayMinutes (Phase 21) - but "
                + "PredictionSnapshot still has no field distinguishing real from mock disruption provenance, "
                + "so mock-data exclusion cannot yet be mechanically guaranteed. Reporting INSUFFICIENT_DATA "
                + "rather than risk treating mock-sourced disruption impact as empirical evidence; closing this "
                + "provenance gap is the recommended next step before any real candidate search is attempted.";
        return CalibrationAssessment.blocked(
                PARAMETER_NAME, CalibrationBlockerReason.MOCK_DATA_ONLY, explanation, realSampleCount);
    }
}
