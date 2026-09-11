package com.railpredictor.evaluation;

import com.railpredictor.model.domain.CalibrationAssessment;
import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Attempts to empirically calibrate the Phase 19 {@code HeuristicDisruptionImpactPolicy}'s default
 * per-type delay minutes (spec item 9) against real evaluated outcomes.
 *
 * <p><b>Why this always reports {@code METRIC_STRUCTURALLY_UNAFFECTED}:</b> exactly the same
 * reason as {@link HistoricalWeightCalibrationAssessor} - {@code disruptionImpactMinutes} only
 * ever feeds the separate, unevaluated {@code predictedTotalDelayMinutes}, never the evaluated
 * {@code predictedNextStationDelayMinutes}. No amount of real disruption data can change this
 * conclusion under the current evaluation scope.
 *
 * <p>A second, independent limitation, documented here rather than acted on (since the structural
 * blocker already applies regardless): {@link PredictionSnapshot} persists no field distinguishing
 * whether an active disruption came from the real RailRadar-backed provider or
 * {@code MockRailwayDisruptionProvider} - so even if the metric were unblocked in a future phase,
 * this codebase could not yet mechanically exclude mock disruption data from a calibration
 * attempt (spec item 9's "mock disruptions must never be treated as empirical calibration
 * evidence" requirement). That gap would need to be closed before this assessor could ever
 * progress beyond {@code INSUFFICIENT_DATA}.
 */
@Component
public class DisruptionImpactCalibrationAssessor {

    private static final String PARAMETER_NAME =
            "railway-disruption-impact.* (HeuristicDisruptionImpactPolicy's per-type default delay minutes)";

    /** @param evaluatedSnapshots already-evaluated (EXACT/APPROXIMATE) snapshots only. */
    public CalibrationAssessment assess(List<PredictionSnapshot> evaluatedSnapshots) {
        Objects.requireNonNull(evaluatedSnapshots, "evaluatedSnapshots");

        int withDisruptionImpact = (int) evaluatedSnapshots.stream()
                .filter(s -> s.disruptionImpactMinutes() != null)
                .count();

        String explanation = "predictedNextStationDelayMinutes (the only quantity this codebase evaluates) is "
                + "computed as currentDelayMinutes + predictedExtraDelayMinutes only - disruptionImpactMinutes "
                + "never contributes to it, only to the separate, unevaluated predictedTotalDelayMinutes. "
                + "Calibrating any railway-disruption-impact.* default against the evaluated metric is therefore "
                + "structurally impossible with the current evaluation scope, regardless of how many of the "
                + evaluatedSnapshots.size() + " evaluated snapshots (" + withDisruptionImpact
                + " with a recorded disruption impact) exist. A second, independent gap: no field on "
                + "PredictionSnapshot distinguishes real from mock disruption provenance, so mock-data exclusion "
                + "could not yet be enforced even if the metric were unblocked in a future phase.";

        return CalibrationAssessment.blocked(
                PARAMETER_NAME, CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED, explanation,
                evaluatedSnapshots.size());
    }
}
