package com.railpredictor.prediction;

import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationResult;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.model.enums.WeatherCondition;
import java.time.DayOfWeek;
import java.time.Month;
import java.util.List;

/** Reusable fixtures for prediction-component tests. */
final class PredictionFixtures {

    private PredictionFixtures() {
    }

    static Station station(String code) {
        return new Station(code, code + " Station");
    }

    static LiveTrainData train(int currentDelayMinutes, Double remainingDistanceKm, Double speedKmh) {
        return new LiveTrainData("12345", "Test Express", TrainStatus.RUNNING, currentDelayMinutes,
                station("NDLS"), station("GZB"), 10.0, remainingDistanceKm, speedKmh);
    }

    static RouteSection section() {
        return section(null);
    }

    static RouteSection section(Double distanceKm) {
        return new RouteSection(station("NDLS"), station("GZB"), distanceKm);
    }

    static HistoricalDelay historicalDelay(double averageDelayMinutes, int sampleCount) {
        // Deliberately not DataProvenance.MOCK: most tests using this fixture aren't concerned
        // with provenance, and shouldn't incidentally trip the mock-data warning/factor text.
        return historicalDelay(averageDelayMinutes, sampleCount, "test-fixture");
    }

    static HistoricalDelay historicalDelay(double averageDelayMinutes, int sampleCount, String source) {
        return new HistoricalDelay("12345", section(), DayOfWeek.WEDNESDAY, Month.SEPTEMBER, null,
                averageDelayMinutes, averageDelayMinutes, 0.0, sampleCount, source);
    }

    static WeatherData weather(WeatherCondition condition) {
        return new WeatherData(condition, 20.0, 5000.0, 0.0, "mock-provider");
    }

    static SimulationResult simulationResult(int direct, int cascade, int recovered, int net) {
        return new SimulationResult(List.of(), List.of(), direct, cascade, recovered, net);
    }

    static SimulationResult simulationResultWithTriggeredCount(int triggeredCount) {
        List<DisruptionResult> disruptions = new java.util.ArrayList<>();
        for (int i = 0; i < triggeredCount; i++) {
            disruptions.add(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 1, "triggered #" + i));
        }
        return new SimulationResult(disruptions, List.of(), triggeredCount, 0, 0, triggeredCount);
    }
}
