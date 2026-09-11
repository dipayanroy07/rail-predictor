package com.railpredictor.exception;

/**
 * The train has no next station (it has already reached its final stop, or been terminated), so
 * there is no future arrival left to predict. Not a {@link RailRadarException} - the live data
 * itself was valid, this is a legitimate outcome the pipeline declines to fabricate a result for.
 */
public class PredictionNotApplicableException extends RuntimeException {

    public PredictionNotApplicableException(String trainNumber) {
        super("Cannot predict an ETA for train '" + trainNumber
                + "': it has no next station (already at its final stop, or terminated)");
    }
}
