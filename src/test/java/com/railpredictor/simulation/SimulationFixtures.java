package com.railpredictor.simulation;

import com.railpredictor.config.DisruptionProperties;
import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.config.SpeedRestrictionConfig;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.TrainStatus;

/** Reusable fixtures for disruption model tests: a train/section, and a way to build a
 * {@code DisruptionProperties} where only the config under test matters. */
final class SimulationFixtures {

    private SimulationFixtures() {
    }

    static Station station(String code) {
        return new Station(code, code + " Station");
    }

    static LiveTrainData train(Double speedKmh) {
        return new LiveTrainData("12345", "Test Express", TrainStatus.RUNNING, 5,
                station("NDLS"), station("GZB"), 10.0, 25.0, speedKmh);
    }

    static RouteSection section(Double distanceKm) {
        return new RouteSection(station("NDLS"), station("GZB"), distanceKm);
    }

    static SimulationContext context(
            boolean heavyRain, boolean denseFog, boolean highCongestion,
            Double speedRestrictionKmh, boolean engineeringBlock, boolean signalHalt,
            WeatherData weather, long seed) {
        return new SimulationContext(train(80.0), section(50.0), weather, null,
                heavyRain, denseFog, highCongestion, speedRestrictionKmh, engineeringBlock, signalHalt, seed);
    }

    static SimulationContext allDisabled(long seed) {
        return context(false, false, false, null, false, false, null, seed);
    }

    private static final ProbabilityDelayConfig UNUSED = new ProbabilityDelayConfig(0, 0, 0);
    private static final SpeedRestrictionConfig UNUSED_SPEED = new SpeedRestrictionConfig(0);

    static DisruptionProperties propertiesWithHeavyRain(ProbabilityDelayConfig config) {
        return new DisruptionProperties(config, UNUSED, UNUSED, UNUSED, UNUSED, UNUSED_SPEED);
    }

    static DisruptionProperties propertiesWithDenseFog(ProbabilityDelayConfig config) {
        return new DisruptionProperties(UNUSED, config, UNUSED, UNUSED, UNUSED, UNUSED_SPEED);
    }

    static DisruptionProperties propertiesWithHighCongestion(ProbabilityDelayConfig config) {
        return new DisruptionProperties(UNUSED, UNUSED, config, UNUSED, UNUSED, UNUSED_SPEED);
    }

    static DisruptionProperties propertiesWithEngineeringBlock(ProbabilityDelayConfig config) {
        return new DisruptionProperties(UNUSED, UNUSED, UNUSED, config, UNUSED, UNUSED_SPEED);
    }

    static DisruptionProperties propertiesWithSignalHalt(ProbabilityDelayConfig config) {
        return new DisruptionProperties(UNUSED, UNUSED, UNUSED, UNUSED, config, UNUSED_SPEED);
    }

    static DisruptionProperties propertiesWithSpeedRestriction(SpeedRestrictionConfig config) {
        return new DisruptionProperties(UNUSED, UNUSED, UNUSED, UNUSED, UNUSED, config);
    }
}
