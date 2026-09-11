package com.railpredictor.model.domain;

import java.util.Set;
import java.util.TreeSet;

/**
 * Shared vocabulary for the free-form {@code source} field on data that can come from either a
 * mock provider or a real one (e.g. {@link WeatherData}, {@link HistoricalDelay}), so every
 * mock/unavailable implementation tags itself the same way rather than each inventing its own
 * string. Real providers are free to use any other value (e.g. a vendor name).
 */
public final class DataProvenance {

    /** Data returned by a mock provider, not derived from any real observation or record. */
    public static final String MOCK = "mock-provider";

    /** No provider could supply data (it failed, or none was configured) - not mock, not real. */
    public static final String UNAVAILABLE = "unavailable";

    /** A real observation extracted from RailRadar's own response - never to be confused with
     * {@link #MOCK} or {@link #UNAVAILABLE}, or with assumed/simulated data (see
     * docs/architecture.md's data classification). */
    public static final String RAILRADAR = "railradar";

    /** A real reading from the Open-Meteo weather API (Phase 17) - see {@code WeatherProvider}. */
    public static final String OPENMETEO = "open-meteo";

    private DataProvenance() {
    }

    /**
     * Combines the distinct sources behind an aggregated statistic. A single source passes
     * through unchanged; more than one produces an explicit {@code "mixed(...)"} label - an
     * aggregate built from e.g. {@link #RAILRADAR} and {@link #MOCK} observations must never be
     * silently reported as just {@link #RAILRADAR}.
     *
     * @throws IllegalArgumentException if {@code sources} is empty - there is nothing to combine
     */
    public static String combine(Set<String> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        if (sources.size() == 1) {
            return sources.iterator().next();
        }
        return "mixed(" + String.join(",", new TreeSet<>(sources)) + ")";
    }
}
