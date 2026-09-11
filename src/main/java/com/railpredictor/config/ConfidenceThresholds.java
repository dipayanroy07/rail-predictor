package com.railpredictor.config;

/** Score cutoffs (0-100) used to bucket a numeric confidence score into a {@code ConfidenceLevel}. */
public record ConfidenceThresholds(double highMinimumScore, double mediumMinimumScore, double lowMinimumScore) {

    public ConfidenceThresholds {
        requireInRange(highMinimumScore, "highMinimumScore");
        requireInRange(mediumMinimumScore, "mediumMinimumScore");
        requireInRange(lowMinimumScore, "lowMinimumScore");
        if (mediumMinimumScore > highMinimumScore) {
            throw new IllegalArgumentException(
                    "prediction.confidence.thresholds.medium-minimum-score must be <= high-minimum-score");
        }
        if (lowMinimumScore > mediumMinimumScore) {
            throw new IllegalArgumentException(
                    "prediction.confidence.thresholds.low-minimum-score must be <= medium-minimum-score");
        }
    }

    private static void requireInRange(double value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(
                    "prediction.confidence.thresholds." + field + " must be between 0 and 100: " + value);
        }
    }
}
