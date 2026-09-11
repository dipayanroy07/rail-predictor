package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.enums.TrainStatus;
import org.junit.jupiter.api.Test;

class LiveTrainDataTest {

    @Test
    void allowsNullNextStationAndUnknownSpeedOrRemainingDistance() {
        // A terminated train has no next station, and speed/remaining distance may be unreported.
        assertDoesNotThrow(() -> new LiveTrainData(
                "12345", "Test Express", TrainStatus.TERMINATED, 5,
                DomainFixtures.station("NDLS"), null, 100.0, null, null));
    }

    @Test
    void allowsZeroDistanceAndZeroSpeed() {
        assertDoesNotThrow(() -> new LiveTrainData(
                "12345", "Test Express", TrainStatus.SCHEDULED, 0,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 0.0, 0.0, 0.0));
    }

    @Test
    void rejectsNegativeDelay() {
        assertThrows(IllegalArgumentException.class, () -> new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, -1,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 10.0, 25.0, 80.0));
    }

    @Test
    void rejectsNegativeDistanceFromOrigin() {
        assertThrows(IllegalArgumentException.class, () -> new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, 5,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), -1.0, 25.0, 80.0));
    }

    @Test
    void rejectsNegativeRemainingDistanceAndSpeed() {
        assertThrows(IllegalArgumentException.class, () -> new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, 5,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 10.0, -1.0, 80.0));
        assertThrows(IllegalArgumentException.class, () -> new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, 5,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 10.0, 25.0, -1.0));
    }

    @Test
    void rejectsBlankTrainNumberOrName() {
        assertThrows(IllegalArgumentException.class, () -> new LiveTrainData(
                " ", "Test Express", TrainStatus.RUNNING, 5,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 10.0, 25.0, 80.0));
        assertThrows(IllegalArgumentException.class, () -> new LiveTrainData(
                "12345", " ", TrainStatus.RUNNING, 5,
                DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 10.0, 25.0, 80.0));
    }
}
