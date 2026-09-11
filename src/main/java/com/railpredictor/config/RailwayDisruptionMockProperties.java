package com.railpredictor.config;

import com.railpredictor.model.domain.RailwayDisruptionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single fixed disruption {@code MockRailwayDisruptionProvider} can return, until (if ever) a
 * real operational-disruption source is integrated (see docs/architecture.md's Phase 18 research
 * notes on why none was selected). Mock data, not a measurement.
 *
 * <p>{@code enabled=false} (the default) means the mock provider reports
 * {@link com.railpredictor.model.domain.RailwayDisruptionAvailability#AVAILABLE} with an empty
 * list for every query - "queried successfully, nothing configured", not "unavailable". Setting
 * {@code enabled=true} makes it also return this one configured disruption whenever a query's
 * (trainNumber, fromStationCode, toStationCode) matches it (a blank/unset {@code trainNumber} here
 * means the configured disruption is route-wide - it matches any queried train).
 */
@ConfigurationProperties(prefix = "railway-disruption.mock")
public record RailwayDisruptionMockProperties(
        boolean enabled,
        RailwayDisruptionType type,
        String trainNumber,
        String fromStationCode,
        String toStationCode,
        Double restrictedSpeedKmh,
        String severity) {

    public RailwayDisruptionMockProperties {
        trainNumber = blankToNull(trainNumber);
        severity = blankToNull(severity);
        if (enabled) {
            if (type == null) {
                throw new IllegalArgumentException("railway-disruption.mock.type must be set when enabled=true");
            }
            if (fromStationCode == null || fromStationCode.isBlank()) {
                throw new IllegalArgumentException("railway-disruption.mock.from-station-code must be set when enabled=true");
            }
            if (toStationCode == null || toStationCode.isBlank()) {
                throw new IllegalArgumentException("railway-disruption.mock.to-station-code must be set when enabled=true");
            }
        }
        if (restrictedSpeedKmh != null && restrictedSpeedKmh < 0) {
            throw new IllegalArgumentException(
                    "railway-disruption.mock.restricted-speed-kmh must not be negative: " + restrictedSpeedKmh);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
