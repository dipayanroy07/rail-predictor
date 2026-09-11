package com.railpredictor.exception;

/** RailRadar rejected our API key (missing or invalid). Maps to HTTP 401 - a configuration
 * problem, not a transient failure, so callers shouldn't retry without fixing the key. */
public class RailRadarAuthenticationException extends RailRadarException {

    public RailRadarAuthenticationException() {
        super("RailRadar rejected the configured API key");
    }
}
