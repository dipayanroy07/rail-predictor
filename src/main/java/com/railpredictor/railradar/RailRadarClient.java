package com.railpredictor.railradar;

import com.railpredictor.config.RailRadarProperties;
import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.exception.RailRadarAuthenticationException;
import com.railpredictor.exception.RailRadarException;
import com.railpredictor.exception.RailRadarRateLimitedException;
import com.railpredictor.exception.RailRadarUnavailableException;
import com.railpredictor.exception.TrainNotFoundException;
import com.railpredictor.railradar.dto.LiveTrainStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * Talks to RailRadar's live-status endpoint over HTTP. See
 * https://railradar.in/docs/live-train-status for the API contract this implements (verified
 * current as of Phase "RailRadar 503 diagnosis": base URL {@code https://api.railradar.in},
 * path {@code /v1/trains/{number}/live}, {@code Authorization: Bearer <key>}).
 *
 * <p>{@link TrainDataProvider} is a synchronous interface, so this client blocks on the
 * underlying reactive {@link WebClient} call rather than exposing a {@code Mono} - the rest of
 * this Spring MVC application is not reactive.
 */
@Component
class RailRadarClient {

    private static final Logger log = LoggerFactory.getLogger(RailRadarClient.class);

    private final WebClient webClient;
    private final RailRadarProperties properties;

    RailRadarClient(WebClient railRadarWebClient, RailRadarProperties properties) {
        this.webClient = railRadarWebClient;
        this.properties = properties;
        log.info("RailRadar client configured: baseUrl={}, apiKeyConfigured={}, timeout={}",
                properties.baseUrl(), properties.apiKey() != null && !properties.apiKey().isBlank(),
                properties.timeout());
    }

    LiveTrainStatusResponse fetchLiveStatus(String trainNumber) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            // Fail fast instead of sending a request we already know RailRadar will reject with
            // 401 - this turns "auth rejected" (a key that's present but wrong) and "no key
            // configured at all" into two distinctly diagnosable log lines, never the credential
            // itself.
            log.warn("RailRadar request for train {} blocked: no RAILRADAR_API_KEY is configured "
                    + "(missing credential, not an upstream failure)", trainNumber);
            throw new RailRadarAuthenticationException();
        }
        try {
            return webClient.get()
                    .uri("/v1/trains/{number}/live", trainNumber)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            response -> Mono.error(new TrainNotFoundException(trainNumber)))
                    .onStatus(status -> status.value() == 401,
                            response -> Mono.error(new RailRadarAuthenticationException()))
                    .onStatus(status -> status.value() == 429,
                            response -> Mono.error(new RailRadarRateLimitedException(trainNumber)))
                    .onStatus(status -> status.isError(),
                            response -> Mono.error(new RailRadarUnavailableException(
                                    "RailRadar returned HTTP " + response.statusCode().value()
                                            + " for train " + trainNumber)))
                    .bodyToMono(LiveTrainStatusResponse.class)
                    .block(properties.timeout());
        } catch (RailRadarException e) {
            log.warn("RailRadar call failed for train {}: category={}, detail={}",
                    trainNumber, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } catch (DecodingException e) {
            log.warn("RailRadar call failed for train {}: category=malformed_response, detail={}",
                    trainNumber, e.getMessage());
            throw new MalformedRailRadarResponseException(
                    "Could not parse RailRadar's response for train " + trainNumber, e);
        } catch (WebClientRequestException e) {
            // The request never got a response at all: DNS failure, refused/reset connection, or
            // the read/write timed out below the transport level - distinct from a timeout that
            // Mono.block(Duration) itself enforces (an IllegalStateException, caught below).
            log.warn("RailRadar call failed for train {}: category=connection_failure, detail={}",
                    trainNumber, e.getMessage());
            throw new RailRadarUnavailableException(
                    "Could not connect to RailRadar for train " + trainNumber + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.warn("RailRadar call failed for train {}: category=timeout_or_unexpected, detail={}",
                    trainNumber, e.getMessage());
            throw new RailRadarUnavailableException(
                    "RailRadar request failed for train " + trainNumber + ": " + e.getMessage(), e);
        }
    }
}
