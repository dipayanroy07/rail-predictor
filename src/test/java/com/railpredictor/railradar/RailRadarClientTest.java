package com.railpredictor.railradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.config.RailRadarProperties;
import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.exception.RailRadarAuthenticationException;
import com.railpredictor.exception.RailRadarRateLimitedException;
import com.railpredictor.exception.RailRadarUnavailableException;
import com.railpredictor.exception.TrainNotFoundException;
import com.railpredictor.railradar.dto.LiveTrainStatusResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** Exercises RailRadarClient's HTTP handling against a local server - no real network calls. */
class RailRadarClientTest {

    private static final String SUCCESS_BODY = """
            {
              "success": true,
              "data": {
                "trainNumber": "12952",
                "trainName": "Rajdhani Express",
                "status": "running",
                "delayMinutes": 12,
                "currentLocation": {"stationCode": "KOTA", "speedKmh": 92.5},
                "nextHalt": {"stationCode": "RTM", "stationName": "Ratlam Jn", "distance": 550.0},
                "route": [
                  {"stationCode": "NDLS", "stationName": "New Delhi", "distance": 0.0},
                  {"stationCode": "KOTA", "stationName": "Kota Jn", "distance": 465.0},
                  {"stationCode": "BCT", "stationName": "Mumbai Central", "distance": 1384.0}
                ]
              },
              "meta": {"traceId": "abc-123", "executionTime": 42, "timestamp": "2026-09-09T10:15:00Z"}
            }
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

    private RailRadarClient clientWithTimeout(Duration timeout) {
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        RailRadarProperties properties = new RailRadarProperties(server.url("/").toString(), "test-key", timeout);
        return new RailRadarClient(webClient, properties);
    }

    private RailRadarClient client() {
        return clientWithTimeout(Duration.ofSeconds(5));
    }

    @Test
    void parsesASuccessfulResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(SUCCESS_BODY));

        LiveTrainStatusResponse response = client().fetchLiveStatus("12952");

        assertThat(response.success()).isTrue();
        assertThat(response.data().trainNumber()).isEqualTo("12952");
        assertThat(response.data().currentLocation().stationCode()).isEqualTo("KOTA");
    }

    @Test
    void mapsHttp404ToTrainNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThrows(TrainNotFoundException.class, () -> client().fetchLiveStatus("99999"));
    }

    @Test
    void mapsHttp401ToAuthenticationException() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(RailRadarAuthenticationException.class, () -> client().fetchLiveStatus("12952"));
    }

    @Test
    void mapsHttp503ToUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThrows(RailRadarUnavailableException.class, () -> client().fetchLiveStatus("12952"));
    }

    @Test
    void mapsMalformedJsonBodyToMalformedResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{ this is not valid json"));

        assertThrows(MalformedRailRadarResponseException.class, () -> client().fetchLiveStatus("12952"));
    }

    @Test
    void mapsHttp429ToRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(RailRadarRateLimitedException.class, () -> client().fetchLiveStatus("12952"));
    }

    @Test
    void blankApiKeyFailsFastWithoutCallingRailRadar() throws IOException {
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        RailRadarProperties properties = new RailRadarProperties(server.url("/").toString(), "", Duration.ofSeconds(5));
        RailRadarClient client = new RailRadarClient(webClient, properties);

        assertThrows(RailRadarAuthenticationException.class, () -> client.fetchLiveStatus("12952"));
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void mapsSlowResponseToUnavailableOnTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(SUCCESS_BODY)
                .setBodyDelay(2, TimeUnit.SECONDS));

        assertThrows(RailRadarUnavailableException.class,
                () -> clientWithTimeout(Duration.ofMillis(200)).fetchLiveStatus("12952"));
    }
}
