package com.railpredictor.model.domain;

import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.TrainStatus;
import java.time.Instant;
import java.util.List;

/** Reusable valid domain objects for tests, so each test only overrides what it's checking. */
final class DomainFixtures {

    private DomainFixtures() {
    }

    static Station station(String code) {
        return new Station(code, code + " Station");
    }

    static RouteSection section() {
        return new RouteSection(station("NDLS"), station("GZB"), 25.0);
    }

    static LiveTrainData liveTrainData() {
        return new LiveTrainData(
                "12345",
                "Test Express",
                TrainStatus.RUNNING,
                5,
                station("NDLS"),
                station("GZB"),
                10.0,
                25.0,
                80.0);
    }

    static ConfidenceScore confidenceScore() {
        return new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of("speed data available"), List.of());
    }

    static PredictionResult predictionResult() {
        return new PredictionResult(
                "12345",
                "Test Express",
                TrainStatus.RUNNING,
                station("NDLS"),
                station("GZB"),
                5,
                10.0,
                25.0,
                80.0,
                com.railpredictor.model.enums.SectionType.NORMAL,
                18.75,
                3,
                0,
                2,
                6,
                Instant.parse("2026-09-09T12:00:00Z"),
                confidenceScore(),
                List.of(),
                List.of(),
                List.of());
    }
}
