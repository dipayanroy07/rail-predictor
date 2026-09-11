package com.railpredictor.model.domain;

/**
 * Everything the simulation engine needs for one run: the train/section being simulated, the
 * (possibly unavailable) weather and historical data, which disruption types are enabled for this
 * run, and a random seed so runs are reproducible - see docs/architecture.md on randomness.
 */
public record SimulationContext(
        LiveTrainData train,
        RouteSection section,
        WeatherData weather,
        HistoricalDelay historicalDelay,
        boolean heavyRainEnabled,
        boolean denseFogEnabled,
        boolean highCongestionEnabled,
        Double speedRestrictionKmh,
        boolean engineeringBlockEnabled,
        boolean signalHaltEnabled,
        long randomSeed) {

    public SimulationContext {
        train = Guard.requireNonNull(train, "train");
        section = Guard.requireNonNull(section, "section");
        if (speedRestrictionKmh != null) {
            Guard.requireNonNegative(speedRestrictionKmh, "speedRestrictionKmh");
        }
    }
}
