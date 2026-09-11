package com.railpredictor.railradar;

import com.railpredictor.model.domain.LiveTrainData;

/**
 * The only way the rest of the application accesses live train data. Callers depend on this
 * interface, never on {@link RailRadarClient} or RailRadar's DTOs directly, so the data source
 * can change without touching callers.
 */
public interface TrainDataProvider {

    /**
     * @param trainNumber the train's number (e.g. "12952")
     * @return the train's current live status
     * @throws com.railpredictor.exception.TrainNotFoundException no such train is running today
     * @throws com.railpredictor.exception.RailRadarException RailRadar could not provide usable data
     */
    LiveTrainData getLiveTrainData(String trainNumber);
}
