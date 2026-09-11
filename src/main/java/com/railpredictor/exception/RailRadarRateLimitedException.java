package com.railpredictor.exception;

/** RailRadar responded with HTTP 429 - our key is valid but has exceeded its request quota. A
 * distinct subtype of {@link RailRadarUnavailableException} so it still maps to the same public
 * 503 contract, while being individually identifiable in logs and, if ever needed, in retry logic. */
public class RailRadarRateLimitedException extends RailRadarUnavailableException {

    public RailRadarRateLimitedException(String trainNumber) {
        super("RailRadar rate-limited this request for train " + trainNumber + " (HTTP 429)");
    }
}
