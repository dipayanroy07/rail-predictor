package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PredictionCollectionResultTest {

    @Test
    void acceptsConsistentCounts() {
        PredictionCollectionResult result = new PredictionCollectionResult(3, 1, 1, 1);

        assertThat(result.trainsAttempted()).isEqualTo(3);
    }

    @Test
    void rejectsCountsThatDoNotSumToTrainsAttempted() {
        assertThatThrownBy(() -> new PredictionCollectionResult(3, 1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new PredictionCollectionResult(-1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
