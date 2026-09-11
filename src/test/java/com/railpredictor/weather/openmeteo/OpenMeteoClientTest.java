package com.railpredictor.weather.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.config.OpenMeteoProperties;
import com.railpredictor.exception.WeatherUnavailableException;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentWeatherResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** Exercises OpenMeteoClient's HTTP handling against a local server - no real network calls,
 * mirroring RailRadarClientTest's exact structure. */
class OpenMeteoClientTest {

    private static final String CLEAR_BODY = """
            {"current":{"time":"2026-09-10T18:00","interval":900,"temperature_2m":29.0,"precipitation":0.00,"visibility":10280.00,"weather_code":0}}
            """;

    private static final String RAIN_BODY = """
            {"current":{"time":"2026-09-10T18:00","interval":900,"temperature_2m":24.0,"precipitation":3.2,"visibility":6000.0,"weather_code":63}}
            """;

    private static final String FOG_BODY = """
            {"current":{"time":"2026-09-10T06:00","interval":900,"temperature_2m":12.0,"precipitation":0.0,"visibility":120.0,"weather_code":45}}
            """;

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private OpenMeteoClient clientWithTimeoutAndKey(Duration timeout, String apiKey) {
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        OpenMeteoProperties properties = new OpenMeteoProperties(server.url("/").toString(), apiKey, timeout, 200.0);
        return new OpenMeteoClient(webClient, properties);
    }

    private OpenMeteoClient client() {
        return clientWithTimeoutAndKey(Duration.ofSeconds(5), "");
    }

    @Test
    void parsesASuccessfulClearWeatherResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(CLEAR_BODY));

        OpenMeteoCurrentWeatherResponse response = client().fetchCurrentWeather(28.6, 77.2);

        assertThat(response.current().weatherCode()).isEqualTo(0);
        assertThat(response.current().temperature2m()).isEqualTo(29.0);
    }

    @Test
    void parsesARainConditionsResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(RAIN_BODY));

        OpenMeteoCurrentWeatherResponse response = client().fetchCurrentWeather(28.6, 77.2);

        assertThat(response.current().weatherCode()).isEqualTo(63);
        assertThat(response.current().precipitation()).isEqualTo(3.2);
    }

    @Test
    void parsesAFogLowVisibilityResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(FOG_BODY));

        OpenMeteoCurrentWeatherResponse response = client().fetchCurrentWeather(28.6, 77.2);

        assertThat(response.current().weatherCode()).isEqualTo(45);
        assertThat(response.current().visibility()).isEqualTo(120.0);
    }

    @Test
    void requestIncludesLatitudeLongitudeAndUtcTimezone() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(CLEAR_BODY));

        client().fetchCurrentWeather(28.6139, 77.209);

        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertThat(path).contains("latitude=28.6139");
        assertThat(path).contains("longitude=77.209");
        assertThat(path).contains("timezone=UTC");
        assertThat(path).doesNotContain("apikey");
    }

    @Test
    void aConfiguredApiKeyIsSentAsAQueryParameterNotAHeader() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(CLEAR_BODY));

        clientWithTimeoutAndKey(Duration.ofSeconds(5), "secret-key-value").fetchCurrentWeather(28.6, 77.2);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("apikey=secret-key-value");
        assertThat(request.getHeader("Authorization")).isNull();
    }

    @Test
    void mapsHttp400ToWeatherUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(400));

        assertThrows(WeatherUnavailableException.class, () -> client().fetchCurrentWeather(28.6, 77.2));
    }

    @Test
    void mapsHttp503ToWeatherUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThrows(WeatherUnavailableException.class, () -> client().fetchCurrentWeather(28.6, 77.2));
    }

    @Test
    void mapsMalformedJsonBodyToWeatherUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{ not valid json"));

        assertThrows(WeatherUnavailableException.class, () -> client().fetchCurrentWeather(28.6, 77.2));
    }

    @Test
    void aMissingRequiredFieldStillParsesLeavingItNull() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"current\":{\"time\":\"2026-09-10T18:00\",\"weather_code\":0}}"));

        OpenMeteoCurrentWeatherResponse response = client().fetchCurrentWeather(28.6, 77.2);

        assertThat(response.current().temperature2m()).isNull();
        assertThat(response.current().precipitation()).isNull();
        assertThat(response.current().visibility()).isNull();
    }

    @Test
    void mapsSlowResponseToWeatherUnavailableOnTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(CLEAR_BODY)
                .setBodyDelay(2, TimeUnit.SECONDS));

        assertThrows(WeatherUnavailableException.class,
                () -> clientWithTimeoutAndKey(Duration.ofMillis(200), "").fetchCurrentWeather(28.6, 77.2));
    }
}
