package com.railpredictor.simulation;

import com.railpredictor.config.RecoveryProperties;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.WeatherCondition;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Estimates how much of a train's accumulated delay can realistically be recovered - through
 * schedule padding, faster running once a section clears, etc. Depends on two conditions
 * available on {@link SimulationContext}: adverse weather (heavy rain/dense fog) reduces how much
 * speed-up is realistic, and remaining distance bounds how much running time is even left in
 * which to make up time. Bounded so the result never exceeds the delay it's recovering from -
 * the final delay after recovery can never go negative.
 */
@Component
public class RecoveryModel {

    private final RecoveryProperties properties;

    public RecoveryModel(RecoveryProperties properties) {
        this.properties = properties;
    }

    public int recover(int accumulatedDelayMinutes, SimulationContext context) {
        Objects.requireNonNull(context, "context");
        if (accumulatedDelayMinutes <= 0) {
            return 0;
        }

        double baseRecoverable = accumulatedDelayMinutes * properties.baseRecoveryFraction();
        double weatherMultiplier = isAdverseWeather(context.weather())
                ? properties.adverseWeatherRecoveryMultiplier()
                : 1.0;
        double weatherAdjustedRecoverable = baseRecoverable * weatherMultiplier;

        Double remainingDistanceKm = context.train().remainingDistanceKm();
        double distanceCap = remainingDistanceKm != null
                ? remainingDistanceKm * properties.maxRecoveryMinutesPerKm()
                : properties.flatMaxRecoveryMinutesWhenDistanceUnknown();

        int recovered = (int) Math.round(Math.min(weatherAdjustedRecoverable, distanceCap));
        return Math.max(0, Math.min(recovered, accumulatedDelayMinutes));
    }

    private static boolean isAdverseWeather(WeatherData weather) {
        return weather != null
                && (weather.condition() == WeatherCondition.HEAVY_RAIN || weather.condition() == WeatherCondition.DENSE_FOG);
    }
}
