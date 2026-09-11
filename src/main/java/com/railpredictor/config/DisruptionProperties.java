package com.railpredictor.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for every disruption model in {@code com.railpredictor.simulation}. */
@ConfigurationProperties(prefix = "simulation.disruption")
public record DisruptionProperties(
        ProbabilityDelayConfig heavyRain,
        ProbabilityDelayConfig denseFog,
        ProbabilityDelayConfig highCongestion,
        ProbabilityDelayConfig engineeringBlock,
        ProbabilityDelayConfig signalHalt,
        SpeedRestrictionConfig speedRestriction) {

    public DisruptionProperties {
        Objects.requireNonNull(heavyRain, "simulation.disruption.heavy-rain must be configured");
        Objects.requireNonNull(denseFog, "simulation.disruption.dense-fog must be configured");
        Objects.requireNonNull(highCongestion, "simulation.disruption.high-congestion must be configured");
        Objects.requireNonNull(engineeringBlock, "simulation.disruption.engineering-block must be configured");
        Objects.requireNonNull(signalHalt, "simulation.disruption.signal-halt must be configured");
        Objects.requireNonNull(speedRestriction, "simulation.disruption.speed-restriction must be configured");
    }
}
