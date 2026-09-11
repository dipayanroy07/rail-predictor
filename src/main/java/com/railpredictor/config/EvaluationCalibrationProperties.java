package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code evaluation.calibration.*} configuration keys (Phase 20). Deliberately small -
 * three properties, each with one clear meaning - per this phase's own instruction against a large
 * number of tuning parameters.
 *
 * <ul>
 *   <li>{@code enabled} (default {@code false}) - whether
 *       {@code GET /api/v1/evaluation/accuracy} additionally computes and attaches a
 *       {@code CalibrationEvaluationReport}. Opt-in, mirroring {@code prediction.evaluation.enabled}:
 *       this is genuinely extra work (subgroup slicing, a confidence audit, two calibration
 *       assessments) that most requests need not pay for.</li>
 *   <li>{@code minimumSampleCount} (default 30) - the fewest real evaluated samples a subgroup
 *       (a weather-availability bucket, a confidence bucket, a train/validation split half) must
 *       have before its metrics are reported as real numbers rather than
 *       {@code CalibrationStatus.INSUFFICIENT_DATA}. 30 is a conventional statistical
 *       rule-of-thumb minimum for a sample mean to be considered even roughly stable - not itself
 *       empirically derived for this domain, and freely overridable.</li>
 *   <li>{@code validationSplit} (default 0.3, i.e. 30%) - the fraction of a time-ordered sample
 *       held out as the later "validation" period when a calibration assessment performs a
 *       train/validation split (see {@code HistoricalWeightCalibrationAssessor}) - the earlier
 *       {@code 1 - validationSplit} fraction is the "training" period a candidate value may be
 *       estimated from. Never overlapping in time by construction.</li>
 *   <li>{@code minimumPracticalImprovementPercent} (default 5.0) - the smallest relative MAE
 *       improvement (validation-period candidate vs. current value) treated as "practically
 *       meaningful" rather than statistical noise - see docs/prediction-model.md's Phase 20 notes
 *       on why a numeric MAE change alone (e.g. 12.10 -&gt; 11.98) is never itself "success".</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "evaluation.calibration")
public record EvaluationCalibrationProperties(
        boolean enabled, int minimumSampleCount, double validationSplit, double minimumPracticalImprovementPercent) {

    public EvaluationCalibrationProperties {
        if (minimumSampleCount < 1) {
            throw new IllegalArgumentException(
                    "evaluation.calibration.minimum-sample-count must be at least 1: " + minimumSampleCount);
        }
        if (validationSplit <= 0.0 || validationSplit >= 1.0) {
            throw new IllegalArgumentException(
                    "evaluation.calibration.validation-split must be strictly between 0 and 1: " + validationSplit);
        }
        if (minimumPracticalImprovementPercent < 0) {
            throw new IllegalArgumentException(
                    "evaluation.calibration.minimum-practical-improvement-percent must not be negative: "
                            + minimumPracticalImprovementPercent);
        }
    }
}
