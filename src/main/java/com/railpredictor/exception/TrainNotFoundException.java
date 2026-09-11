package com.railpredictor.exception;

/** RailRadar has no train matching the given train number (and date). Maps to HTTP 404. */
public class TrainNotFoundException extends RailRadarException {

    public TrainNotFoundException(String trainNumber) {
        super("No train found for train number '" + trainNumber + "'");
    }
}
