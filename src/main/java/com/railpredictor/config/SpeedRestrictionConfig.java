package com.railpredictor.config;

/**
 * Fallback delay for {@code SpeedRestrictionModel} when it can't compute an actual delay from
 * available speed/distance data. An explicit assumption, not a measurement.
 */
public record SpeedRestrictionConfig(int flatDelayMinutesWhenNotComputable) {

    public SpeedRestrictionConfig {
        if (flatDelayMinutesWhenNotComputable < 0) {
            throw new IllegalArgumentException(
                    "flatDelayMinutesWhenNotComputable must not be negative: " + flatDelayMinutesWhenNotComputable);
        }
    }
}
