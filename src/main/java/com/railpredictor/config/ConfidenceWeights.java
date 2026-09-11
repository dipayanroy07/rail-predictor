package com.railpredictor.config;

/**
 * How much each signal contributes to a prediction's confidence score. The final score is the
 * achieved weight as a percentage of the sum of all weights here, so these don't need to sum to
 * 100 - relative size is what matters. Assumed values, not empirically validated - see
 * docs/architecture.md.
 */
public record ConfidenceWeights(
        double liveTrainDataCompleteness,
        double weatherAvailability,
        double historicalDataAvailability,
        double routeInformationQuality,
        double speedAvailability,
        double sectionConditionReliability,
        double simulationStability) {

    public ConfidenceWeights {
        requireNonNegative(liveTrainDataCompleteness, "liveTrainDataCompleteness");
        requireNonNegative(weatherAvailability, "weatherAvailability");
        requireNonNegative(historicalDataAvailability, "historicalDataAvailability");
        requireNonNegative(routeInformationQuality, "routeInformationQuality");
        requireNonNegative(speedAvailability, "speedAvailability");
        requireNonNegative(sectionConditionReliability, "sectionConditionReliability");
        requireNonNegative(simulationStability, "simulationStability");
    }

    private static void requireNonNegative(double value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "prediction.confidence.weights." + field + " must not be negative: " + value);
        }
    }
}
