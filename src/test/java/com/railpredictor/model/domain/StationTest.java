package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StationTest {

    @Test
    void rejectsBlankCodeAndName() {
        assertThrows(IllegalArgumentException.class, () -> new Station(" ", "New Delhi"));
        assertThrows(IllegalArgumentException.class, () -> new Station("NDLS", " "));
    }

    @Test
    void rejectsNullCodeAndName() {
        assertThrows(IllegalArgumentException.class, () -> new Station(null, "New Delhi"));
        assertThrows(IllegalArgumentException.class, () -> new Station("NDLS", null));
    }

    @Test
    void twoArgConstructorLeavesCoordinatesNull() {
        Station station = new Station("NDLS", "New Delhi");

        assertThat(station.latitude()).isNull();
        assertThat(station.longitude()).isNull();
    }

    @Test
    void acceptsValidCoordinates() {
        assertDoesNotThrow(() -> new Station("NDLS", "New Delhi", 28.6, 77.2));
        assertDoesNotThrow(() -> new Station("NDLS", "New Delhi", 90.0, 180.0));
        assertDoesNotThrow(() -> new Station("NDLS", "New Delhi", -90.0, -180.0));
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Station("NDLS", "New Delhi", 90.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Station("NDLS", "New Delhi", -90.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Station("NDLS", "New Delhi", 0.0, 180.1));
        assertThrows(IllegalArgumentException.class, () -> new Station("NDLS", "New Delhi", 0.0, -180.1));
    }
}
