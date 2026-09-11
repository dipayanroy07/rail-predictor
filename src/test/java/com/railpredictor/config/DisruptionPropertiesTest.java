package com.railpredictor.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DisruptionPropertiesTest {

    private static final ProbabilityDelayConfig VALID = new ProbabilityDelayConfig(0.5, 5, 10);
    private static final SpeedRestrictionConfig VALID_SPEED = new SpeedRestrictionConfig(10);

    @Test
    void rejectsAnyMissingSubConfig() {
        assertThrows(NullPointerException.class,
                () -> new DisruptionProperties(null, VALID, VALID, VALID, VALID, VALID_SPEED));
        assertThrows(NullPointerException.class,
                () -> new DisruptionProperties(VALID, VALID, VALID, VALID, VALID, null));
    }
}
