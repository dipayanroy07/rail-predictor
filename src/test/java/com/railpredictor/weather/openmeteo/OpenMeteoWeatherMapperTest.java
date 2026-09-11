package com.railpredictor.weather.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.railpredictor.config.OpenMeteoProperties;
import com.railpredictor.exception.WeatherUnavailableException;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.WeatherCondition;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentConditions;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentWeatherResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpenMeteoWeatherMapperTest {

    private final OpenMeteoWeatherMapper mapper =
            new OpenMeteoWeatherMapper(new OpenMeteoProperties("https://api.open-meteo.com", "", Duration.ofSeconds(5), 200.0));

    private static OpenMeteoCurrentWeatherResponse response(
            String time, Double temperature, Double precipitation, Double visibility, Integer weatherCode) {
        return new OpenMeteoCurrentWeatherResponse(
                new OpenMeteoCurrentConditions(time, temperature, precipitation, visibility, weatherCode));
    }

    // --- Classification ---

    @Test
    void clearSkyCodeMapsToClear() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 29.0, 0.0, 10000.0, 0));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.CLEAR);
    }

    @Test
    void lightRainCodeMapsToRain() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 24.0, 2.0, 8000.0, 61));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.RAIN);
    }

    @Test
    void heavyRainCodeMapsToHeavyRain() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 22.0, 15.0, 3000.0, 65));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.HEAVY_RAIN);
    }

    @Test
    void thunderstormCodeMapsToHeavyRain() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 22.0, 20.0, 2000.0, 95));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.HEAVY_RAIN);
    }

    @Test
    void fogCodeWithVisibilityAboveThresholdMapsToPlainFog() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", 15.0, 0.0, 500.0, 45));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.FOG);
    }

    @Test
    void fogCodeWithVisibilityAtOrBelowThresholdMapsToDenseFog() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", 12.0, 0.0, 150.0, 45));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.DENSE_FOG);
    }

    @Test
    void fogCodeExactlyAtTheThresholdIsDenseFog() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", 12.0, 0.0, 200.0, 45));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.DENSE_FOG);
    }

    @Test
    void fogCodeWithNoVisibilityFigureConservativelyMapsToPlainFogNotDense() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", 12.0, 0.0, null, 45));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.FOG);
    }

    @Test
    void snowCodeIsNotFabricatedIntoAnyExistingCategory() {
        // WeatherCondition has no SNOW value - a known, documented limitation, not a bug.
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", -2.0, 3.0, 4000.0, 73));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    void unrecognizedCodeMapsToUnknown() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", 20.0, 0.0, 9000.0, 4242));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    void missingWeatherCodeMapsToUnknownRatherThanThrowing() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T06:00", 20.0, 0.0, 9000.0, null));
        assertThat(weather.condition()).isEqualTo(WeatherCondition.UNKNOWN);
    }

    // --- Units: no conversion needed ---

    @Test
    void temperaturePrecipitationAndVisibilityPassThroughUnconverted() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 29.3, 4.2, 8123.0, 61));
        assertThat(weather.temperatureCelsius()).isEqualTo(29.3);
        assertThat(weather.precipitationMm()).isEqualTo(4.2);
        assertThat(weather.visibilityMeters()).isEqualTo(8123.0);
    }

    // --- Provenance ---

    @Test
    void everyReadingIsTaggedWithTheOpenMeteoProvenance() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 29.0, 0.0, 10000.0, 0));
        assertThat(weather.source()).isEqualTo(DataProvenance.OPENMETEO);
    }

    // --- observedAt / timestamp semantics ---

    @Test
    void observedAtIsParsedFromTheCurrentTimeFieldAssumingUtc() {
        WeatherData weather = mapper.toWeatherData(response("2026-09-10T18:00", 29.0, 0.0, 10000.0, 0));
        assertThat(weather.observedAt()).isEqualTo(Instant.parse("2026-09-10T18:00:00Z"));
    }

    @Test
    void aMissingTimeFieldLeavesObservedAtNullRatherThanFabricatingIt() {
        WeatherData weather = mapper.toWeatherData(response(null, 29.0, 0.0, 10000.0, 0));
        assertThat(weather.observedAt()).isNull();
    }

    @Test
    void anUnparseableTimeFieldLeavesObservedAtNullRatherThanThrowing() {
        WeatherData weather = mapper.toWeatherData(response("not-a-timestamp", 29.0, 0.0, 10000.0, 0));
        assertThat(weather.observedAt()).isNull();
    }

    // --- Malformed response ---

    @Test
    void aResponseWithNoCurrentBlockThrowsWeatherUnavailable() {
        OpenMeteoCurrentWeatherResponse response = new OpenMeteoCurrentWeatherResponse(null);
        assertThatThrownBy(() -> mapper.toWeatherData(response))
                .isInstanceOf(WeatherUnavailableException.class);
    }
}
