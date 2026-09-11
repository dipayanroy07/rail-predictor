package com.railpredictor.exception;

/** RailRadar responded, but the body couldn't be parsed, or was missing a field the mapper needs
 * to build a valid {@code LiveTrainData}. We fail rather than invent the missing data. */
public class MalformedRailRadarResponseException extends RailRadarException {

    public MalformedRailRadarResponseException(String message) {
        super(message);
    }

    public MalformedRailRadarResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
