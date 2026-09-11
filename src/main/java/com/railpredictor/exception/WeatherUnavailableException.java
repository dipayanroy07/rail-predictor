package com.railpredictor.exception;

/**
 * A real weather provider (e.g. Open-Meteo) could not supply a reading - unreachable, timed out,
 * returned an error status, or returned an unparseable body. Never reaches a client: {@code
 * PredictionService} catches this (like every other optional-data failure) and degrades to "no
 * weather data" rather than failing the whole prediction - see docs/architecture.md on graceful
 * degradation. Not part of {@code RailRadarException}'s hierarchy - weather is a completely
 * separate external integration with its own failure semantics.
 */
public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(String message) {
        super(message);
    }

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
