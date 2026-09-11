package com.railpredictor.model.domain;

import com.railpredictor.model.enums.WeatherCondition;
import java.time.Instant;

/**
 * Weather at a point relevant to a train's position. {@code source} names where the reading came
 * from (e.g. "mock-provider", "open-meteo"), so mock data is never mistaken for a real observation
 * downstream.
 *
 * <p>{@code observedAt} (Phase 17) is the point in time this reading is actually <em>valid for</em>
 * - not when the application happened to call the provider. This distinction matters: a provider
 * response received "now" can still describe conditions from a few minutes earlier (the provider's
 * own model/observation cadence), and conflating "when we asked" with "when this was true" would
 * misrepresent the reading's real freshness. {@code null} when a provider genuinely has no such
 * timestamp to give - {@link com.railpredictor.weather.MockWeatherProvider}'s reading is a fixed
 * constant with no real observation moment at all, so it is {@code null} there, never a fabricated
 * "now". A real provider that does supply one (e.g. Open-Meteo's own {@code current.time}) must
 * preserve it as-is, never substitute the request/response time instead.
 */
public record WeatherData(
        WeatherCondition condition,
        Double temperatureCelsius,
        Double visibilityMeters,
        Double precipitationMm,
        String source,
        Instant observedAt) {

    /** Pre-Phase-17 shape, preserved so existing callers/tests need not change: {@code observedAt}
     * defaults to {@code null} - correct for every reading produced before this phase (the mock
     * provider never had a real observation moment to report). */
    public WeatherData(
            WeatherCondition condition, Double temperatureCelsius, Double visibilityMeters,
            Double precipitationMm, String source) {
        this(condition, temperatureCelsius, visibilityMeters, precipitationMm, source, null);
    }

    public WeatherData {
        condition = Guard.requireNonNull(condition, "condition");
        if (visibilityMeters != null) {
            Guard.requireNonNegative(visibilityMeters, "visibilityMeters");
        }
        if (precipitationMm != null) {
            Guard.requireNonNegative(precipitationMm, "precipitationMm");
        }
        source = Guard.requireNonBlank(source, "source");
    }
}
