package com.railpredictor.evaluation;

import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Splits already-evaluated {@link PredictionSnapshot}s into an earlier "training/calibration"
 * period and a later "validation" period, ordered strictly by {@code predictionMadeAt} (Phase 20,
 * spec item 7: "avoid random train/test splitting for temporal prediction data"). Every training
 * snapshot's {@code predictionMadeAt} is guaranteed to be no later than every validation
 * snapshot's - a real train/validation calibration attempt must never let a later-period outcome
 * influence a value selected from the earlier period.
 *
 * <p>This is pure split mechanics - it says nothing about whether any given parameter's
 * calibration is actually unblocked by this data (see {@code HistoricalWeightCalibrationAssessor}/
 * {@code DisruptionImpactCalibrationAssessor} for why, today, both remain structurally blocked
 * regardless of how this split comes out).
 */
@Component
public class ChronologicalSplitter {

    public record Split(List<PredictionSnapshot> training, List<PredictionSnapshot> validation) {}

    /** @param validationSplit fraction (0,1) exclusive of the total assigned to the later, validation period. */
    public Split split(List<PredictionSnapshot> snapshots, double validationSplit) {
        Objects.requireNonNull(snapshots, "snapshots");
        if (validationSplit <= 0.0 || validationSplit >= 1.0) {
            throw new IllegalArgumentException("validationSplit must be strictly between 0 and 1: " + validationSplit);
        }

        List<PredictionSnapshot> chronological = snapshots.stream()
                .sorted(Comparator.comparing(PredictionSnapshot::predictionMadeAt))
                .toList();

        int total = chronological.size();
        int validationCount = (int) Math.round(total * validationSplit);
        int trainingCount = total - validationCount;

        return new Split(chronological.subList(0, trainingCount), chronological.subList(trainingCount, total));
    }
}
