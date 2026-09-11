package com.railpredictor.simulation;

import java.util.Random;

/**
 * Shared "roll the dice" logic for the probability-driven disruption models (heavy rain, dense
 * fog, congestion, engineering block, signal halt). {@code SpeedRestrictionModel} doesn't use
 * this - it's deterministic given its input, nothing to randomly decide.
 */
final class ProbabilisticDisruption {

    private ProbabilisticDisruption() {
    }

    record Outcome(boolean triggered, int delayMinutes) {
    }

    static Outcome roll(boolean enabled, double triggerProbability, int minDelayMinutes, int maxDelayMinutes, Random random) {
        if (!enabled || random.nextDouble() >= triggerProbability) {
            return new Outcome(false, 0);
        }
        return new Outcome(true, randomDelayInRange(random, minDelayMinutes, maxDelayMinutes));
    }

    static int randomDelayInRange(Random random, int minDelayMinutes, int maxDelayMinutes) {
        return minDelayMinutes == maxDelayMinutes
                ? minDelayMinutes
                : minDelayMinutes + random.nextInt(maxDelayMinutes - minDelayMinutes + 1);
    }
}
