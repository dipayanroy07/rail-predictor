package com.railpredictor.model.domain;

/**
 * A railway station identified by its code (e.g. "NDLS") and display name. {@code latitude}/
 * {@code longitude} are null when not known - not every caller can derive them (e.g.
 * {@code RouteProvider} in Phase 4 has no source for a section's endpoint coordinates), but
 * {@code LiveTrainDataMapper} (Phase 3) populates them from RailRadar's route data where
 * available, since Phase 13's prediction pipeline needs them to call {@code WeatherProvider}.
 */
public record Station(String code, String name, Double latitude, Double longitude) {

    public Station(String code, String name) {
        this(code, name, null, null);
    }

    public Station {
        code = Guard.requireNonBlank(code, "code");
        name = Guard.requireNonBlank(name, "name");
        if (latitude != null) {
            Guard.requireInRange(latitude, -90, 90, "latitude");
        }
        if (longitude != null) {
            Guard.requireInRange(longitude, -180, 180, "longitude");
        }
    }
}
