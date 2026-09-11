package com.railpredictor.weather;

import com.railpredictor.model.domain.WeatherData;

/**
 * The only way the rest of the application accesses weather data. Callers depend on this
 * interface, never on a specific weather API, so the mock implementation can be swapped for a
 * real one (Phase 17) without touching callers.
 */
public interface WeatherProvider {

    /**
     * @param latitude  degrees, -90 to 90
     * @param longitude degrees, -180 to 180
     */
    WeatherData getWeather(double latitude, double longitude);
}
