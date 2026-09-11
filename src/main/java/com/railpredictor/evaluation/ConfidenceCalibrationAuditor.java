package com.railpredictor.evaluation;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.EvaluationCalibrationProperties;
import com.railpredictor.model.domain.ConfidenceBucketMetrics;
import com.railpredictor.model.domain.ConfidenceCalibrationReport;
import com.railpredictor.model.domain.ErrorDistribution;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.model.enums.ConfidenceLevel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Checks whether this system's confidence score actually correlates with real prediction
 * reliability (Phase 20) - buckets real evaluated snapshots by the same
 * {@link ConfidenceThresholds} the live {@code ConfidenceCalculator} uses, then compares each
 * bucket's real absolute error. Never modifies the confidence formula itself - this is an audit,
 * not a recalibration (see this phase's own instructions against automatic confidence
 * recalibration).
 */
@Component
public class ConfidenceCalibrationAuditor {

    /** Best-to-worst order - the order monotonicity is checked in. */
    private static final List<ConfidenceLevel> ORDER =
            List.of(ConfidenceLevel.HIGH, ConfidenceLevel.MEDIUM, ConfidenceLevel.LOW, ConfidenceLevel.VERY_LOW);

    private final ConfidenceThresholds thresholds;
    private final EvaluationCalibrationProperties calibrationProperties;
    private final PredictionAccuracyCalculator accuracyCalculator;
    private final ErrorDistributionCalculator distributionCalculator;

    public ConfidenceCalibrationAuditor(
            ConfidenceProperties confidenceProperties,
            EvaluationCalibrationProperties calibrationProperties,
            PredictionAccuracyCalculator accuracyCalculator,
            ErrorDistributionCalculator distributionCalculator) {
        this.thresholds = confidenceProperties.thresholds();
        this.calibrationProperties = calibrationProperties;
        this.accuracyCalculator = accuracyCalculator;
        this.distributionCalculator = distributionCalculator;
    }

    /** @param evaluatedSnapshots already-evaluated (EXACT/APPROXIMATE) snapshots only - this
     *                            method does not filter by evaluation status itself. */
    public ConfidenceCalibrationReport audit(List<PredictionSnapshot> evaluatedSnapshots) {
        Objects.requireNonNull(evaluatedSnapshots, "evaluatedSnapshots");

        Map<ConfidenceLevel, List<Integer>> errorsByLevel = new EnumMap<>(ConfidenceLevel.class);
        for (ConfidenceLevel level : ConfidenceLevel.values()) {
            errorsByLevel.put(level, new ArrayList<>());
        }
        for (PredictionSnapshot snapshot : evaluatedSnapshots) {
            errorsByLevel.get(levelFor(snapshot.confidenceScore())).add(snapshot.errorMinutes());
        }

        List<ConfidenceBucketMetrics> buckets = new ArrayList<>();
        for (ConfidenceLevel level : ORDER) {
            List<Integer> errors = errorsByLevel.get(level);
            boolean sufficient = errors.size() >= calibrationProperties.minimumSampleCount();
            double mae = accuracyCalculator.compute(errors).meanAbsoluteError();
            ErrorDistribution distribution = distributionCalculator.compute(errors);
            buckets.add(new ConfidenceBucketMetrics(level, errors.size(), sufficient, mae, distribution));
        }

        List<ConfidenceBucketMetrics> sufficientBucketsInOrder =
                buckets.stream().filter(ConfidenceBucketMetrics::sufficientSample).toList();

        if (sufficientBucketsInOrder.size() < 2) {
            return new ConfidenceCalibrationReport(buckets, false,
                    "Fewer than two confidence buckets have a sufficient sample "
                            + "(evaluation.calibration.minimum-sample-count=" + calibrationProperties.minimumSampleCount()
                            + ") - not enough real evidence to assess whether confidence correlates with error.");
        }

        boolean monotonic = true;
        for (int i = 0; i + 1 < sufficientBucketsInOrder.size(); i++) {
            if (sufficientBucketsInOrder.get(i).meanAbsoluteError() > sufficientBucketsInOrder.get(i + 1).meanAbsoluteError()) {
                monotonic = false;
                break;
            }
        }

        String explanation = monotonic
                ? "Mean absolute error is non-increasing as confidence rises across every bucket with a "
                        + "sufficient sample - consistent with (but not proof of) confidence correlating with "
                        + "real reliability."
                : "At least one lower-confidence bucket had a lower mean absolute error than a "
                        + "higher-confidence bucket, among buckets with a sufficient sample - confidence does "
                        + "not currently correlate cleanly with real error on this data.";

        return new ConfidenceCalibrationReport(buckets, monotonic, explanation);
    }

    private ConfidenceLevel levelFor(double score) {
        if (score >= thresholds.highMinimumScore()) {
            return ConfidenceLevel.HIGH;
        }
        if (score >= thresholds.mediumMinimumScore()) {
            return ConfidenceLevel.MEDIUM;
        }
        if (score >= thresholds.lowMinimumScore()) {
            return ConfidenceLevel.LOW;
        }
        return ConfidenceLevel.VERY_LOW;
    }
}
