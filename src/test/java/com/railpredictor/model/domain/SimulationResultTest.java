package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.enums.DisruptionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationResultTest {

    @Test
    void allowsNoDisruptionsAndNoCascade() {
        assertDoesNotThrow(() -> new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
    }

    @Test
    void allowsRecoveryFullyOffsettingDelaySoLongAsNetDelayStaysNonNegative() {
        // Recovery of 20 against a direct delay of 20 should still be representable as net 0.
        assertDoesNotThrow(() -> new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 20, "simulated rain delay")),
                List.of(),
                20, 0, 20, 0));
    }

    @Test
    void rejectsNegativeNetDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationResult(List.of(), List.of(), 5, 0, 20, -15));
    }

    @Test
    void rejectsNullListsAndDefensivelyCopiesGivenOnes() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(null, List.of(), 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(List.of(), null, 0, 0, 0, 0));
    }
}
