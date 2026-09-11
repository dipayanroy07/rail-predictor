package com.railpredictor.exception;

/** RailRadar could not be reached or returned an error, and it may work again on retry: network
 * failure, timeout, HTTP 429 (rate limited), or 5xx/503. */
public class RailRadarUnavailableException extends RailRadarException {

    public RailRadarUnavailableException(String message) {
        super(message);
    }

    public RailRadarUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
