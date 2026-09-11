package com.railpredictor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.domain.RailwayDisruptionType;
import org.junit.jupiter.api.Test;

class RailwayDisruptionMockPropertiesTest {

    @Test
    void disabledAcceptsAllNullFields() {
        assertDoesNotThrow(() -> new RailwayDisruptionMockProperties(false, null, null, null, null, null, null));
    }

    @Test
    void enabledRequiresType() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionMockProperties(
                true, null, "12952", "KOTA", "RTM", null, null));
    }

    @Test
    void enabledRequiresFromStationCode() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.CONGESTION, "12952", null, "RTM", null, null));
    }

    @Test
    void enabledRequiresToStationCode() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.CONGESTION, "12952", "KOTA", null, null, null));
    }

    @Test
    void enabledWithoutATrainNumberIsValidAndTreatedAsRouteWide() {
        assertDoesNotThrow(() -> new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.ENGINEERING_BLOCK, null, "KOTA", "RTM", null, null));
    }

    @Test
    void rejectsANegativeRestrictedSpeed() {
        assertThrows(IllegalArgumentException.class, () -> new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, "12952", "KOTA", "RTM", -1.0, null));
    }

    @Test
    void blankTrainNumberAndSeverityAreNormalizedToNull() {
        RailwayDisruptionMockProperties properties = new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.ENGINEERING_BLOCK, " ", "KOTA", "RTM", null, " ");

        assertThat(properties.trainNumber()).isNull();
        assertThat(properties.severity()).isNull();
    }
}
