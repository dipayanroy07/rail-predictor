package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ProbabilisticDisruptionTest {

    @Test
    void disabledNeverTriggersRegardlessOfProbability() {
        var outcome = ProbabilisticDisruption.roll(false, 1.0, 5, 10, new Random(1));

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.delayMinutes()).isZero();
    }

    @Test
    void probabilityOneAlwaysTriggersWithDelayInRange() {
        var outcome = ProbabilisticDisruption.roll(true, 1.0, 5, 10, new Random(1));

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.delayMinutes()).isBetween(5, 10);
    }

    @Test
    void probabilityZeroNeverTriggers() {
        var outcome = ProbabilisticDisruption.roll(true, 0.0, 5, 10, new Random(1));

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.delayMinutes()).isZero();
    }

    @Test
    void sameSeedProducesTheSameOutcome() {
        var first = ProbabilisticDisruption.roll(true, 0.5, 5, 30, new Random(123));
        var second = ProbabilisticDisruption.roll(true, 0.5, 5, 30, new Random(123));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void minEqualsMaxAlwaysReturnsThatExactDelay() {
        var outcome = ProbabilisticDisruption.roll(true, 1.0, 15, 15, new Random(7));

        assertThat(outcome.delayMinutes()).isEqualTo(15);
    }
}
