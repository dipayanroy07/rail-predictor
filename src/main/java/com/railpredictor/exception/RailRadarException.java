package com.railpredictor.exception;

/** Base type for everything that can go wrong talking to RailRadar. Unchecked: callers that
 * don't specifically handle a RailRadar failure shouldn't be forced to declare it. */
public abstract class RailRadarException extends RuntimeException {

    protected RailRadarException(String message) {
        super(message);
    }

    protected RailRadarException(String message, Throwable cause) {
        super(message, cause);
    }
}
