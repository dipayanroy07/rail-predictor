package com.railpredictor.weather.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.exception.WeatherUnavailableException;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.WeatherCondition;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentWeatherResponse;
import org.junit.jupiter.api.Test;

class OpenMeteoWeatherProviderTest {

    private final OpenMeteoClient client = mock(OpenMeteoClient.class);
    private final OpenMeteoWeatherMapper mapper = mock(OpenMeteoWeatherMapper.class);
    private final OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider(client, mapper);

    @Test
    void delegatesToTheClientAndMapperInOrder() {
        OpenMeteoCurrentWeatherResponse rawResponse = mock(OpenMeteoCurrentWeatherResponse.class);
        WeatherData expected = new WeatherData(WeatherCondition.CLEAR, 25.0, 10000.0, 0.0, DataProvenance.OPENMETEO);
        when(client.fetchCurrentWeather(28.6, 77.2)).thenReturn(rawResponse);
        when(mapper.toWeatherData(rawResponse)).thenReturn(expected);

        WeatherData result = provider.getWeather(28.6, 77.2);

        assertThat(result).isEqualTo(expected);
        verify(client).fetchCurrentWeather(eq(28.6), eq(77.2));
        verify(mapper).toWeatherData(rawResponse);
    }

    @Test
    void rejectsOutOfRangeLatitudeOrLongitude() {
        assertThatThrownBy(() -> provider.getWeather(91.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getWeather(-91.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getWeather(0.0, 181.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getWeather(0.0, -181.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aClientFailurePropagatesUnchangedRatherThanBeingSwallowed() {
        // This provider never substitutes mock data on failure - PredictionService is where
        // degradation happens, not here.
        when(client.fetchCurrentWeather(28.6, 77.2)).thenThrow(new WeatherUnavailableException("timed out"));

        assertThatThrownBy(() -> provider.getWeather(28.6, 77.2)).isInstanceOf(WeatherUnavailableException.class);
    }
}
