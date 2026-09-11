package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.railpredictor.model.enums.ConfidenceLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfidenceScoreTest {

    @Test
    void acceptsScoreAtBounds() {
        assertDoesNotThrow(() -> new ConfidenceScore(0.0, ConfidenceLevel.VERY_LOW, List.of(), List.of()));
        assertDoesNotThrow(() -> new ConfidenceScore(100.0, ConfidenceLevel.HIGH, List.of(), List.of()));
    }

    @Test
    void rejectsScoreOutsideZeroToHundred() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceScore(-0.1, ConfidenceLevel.LOW, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceScore(100.1, ConfidenceLevel.HIGH, List.of(), List.of()));
    }

    @Test
    void listsAreDefensivelyCopiedAndImmutable() {
        List<String> mutableFactors = new ArrayList<>(List.of("speed data available"));
        ConfidenceScore score = new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, mutableFactors, List.of());

        mutableFactors.add("mutated after construction");

        assertTrue(score.contributingFactors().size() == 1);
        assertThrows(UnsupportedOperationException.class, () -> score.contributingFactors().add("nope"));
    }
}
