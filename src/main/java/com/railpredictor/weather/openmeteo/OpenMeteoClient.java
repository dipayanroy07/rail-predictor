package com.railpredictor.weather.openmeteo;

import com.railpredictor.config.OpenMeteoProperties;
import com.railpredictor.exception.WeatherUnavailableException;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentWeatherResponse;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Talks to Open-Meteo's forecast endpoint over HTTP. See https://open-meteo.com/en/docs for the
 * API contract this implements. Mirrors {@code RailRadarClient}'s exact structure: a synchronous
 * call blocking on the underlying reactive {@link WebClient} (this application is a plain Spring
 * MVC/servlet app, not reactive), one configured timeout bounding the entire request/response, and
 * every failure mode mapped to a single {@link WeatherUnavailableException} - unlike RailRadar,
 * nothing downstream needs to distinguish *why* weather is unavailable (see {@code
 * PredictionService}, which already degrades identically for any weather failure).
 */
@Component
class OpenMeteoClient {

    private final WebClient webClient;
    private final OpenMeteoProperties properties;

    OpenMeteoClient(WebClient openMeteoWebClient, OpenMeteoProperties properties) {
        this.webClient = openMeteoWebClient;
        this.properties = properties;
    }

    OpenMeteoCurrentWeatherResponse fetchCurrentWeather(double latitude, double longitude) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/v1/forecast")
                                .queryParam("latitude", latitude)
                                .queryParam("longitude", longitude)
                                .queryParam("current", "temperature_2m,precipitation,visibility,weather_code")
                                .queryParam("timezone", "UTC");
                        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                            // Open-Meteo's own convention for its paid tier - a query parameter,
                            // never a header. Never logged (see this class's own tests).
                            uriBuilder.queryParam("apikey", properties.apiKey());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            response -> Mono.error(new WeatherUnavailableException(
                                    "Open-Meteo returned HTTP " + response.statusCode().value()
                                            + " for (" + latitude + ", " + longitude + ")")))
                    .bodyToMono(OpenMeteoCurrentWeatherResponse.class)
                    .block(properties.timeout());
        } catch (WeatherUnavailableException e) {
            throw e;
        } catch (DecodingException e) {
            throw new WeatherUnavailableException(
                    "Could not parse Open-Meteo's response for (" + latitude + ", " + longitude + ")", e);
        } catch (RuntimeException e) {
            throw new WeatherUnavailableException(
                    "Open-Meteo request failed for (" + latitude + ", " + longitude + "): " + e.getMessage(), e);
        }
    }
}
