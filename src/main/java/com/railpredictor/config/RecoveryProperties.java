package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures {@code RecoveryModel}'s estimate of how much accumulated delay a train can realistically
 * make up: a base fraction of the delay, reduced under adverse weather, and capped by how much
 * remaining distance/running time is actually left to recover it in. Assumed values, not
 * empirically validated - see docs/architecture.md.
 */
@ConfigurationProperties(prefix = "simulation.recovery")
public record RecoveryProperties(
        double baseRecoveryFraction,
        double adverseWeatherRecoveryMultiplier,
        double maxRecoveryMinutesPerKm,
        int flatMaxRecoveryMinutesWhenDistanceUnknown) {

    public RecoveryProperties {
        if (baseRecoveryFraction < 0 || baseRecoveryFraction > 1) {
            throw new IllegalArgumentException(
                    "simulation.recovery.base-recovery-fraction must be between 0 and 1: " + baseRecoveryFraction);
        }
        if (adverseWeatherRecoveryMultiplier < 0 || adverseWeatherRecoveryMultiplier > 1) {
            throw new IllegalArgumentException(
                    "simulation.recovery.adverse-weather-recovery-multiplier must be between 0 and 1: "
                            + adverseWeatherRecoveryMultiplier);
        }
        if (maxRecoveryMinutesPerKm < 0) {
            throw new IllegalArgumentException(
                    "simulation.recovery.max-recovery-minutes-per-km must not be negative: " + maxRecoveryMinutesPerKm);
        }
        if (flatMaxRecoveryMinutesWhenDistanceUnknown < 0) {
            throw new IllegalArgumentException(
                    "simulation.recovery.flat-max-recovery-minutes-when-distance-unknown must not be negative: "
                            + flatMaxRecoveryMinutesWhenDistanceUnknown);
        }
    }
}
