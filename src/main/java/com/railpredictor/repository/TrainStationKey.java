package com.railpredictor.repository;

/**
 * A distinct (trainNumber, stationCode) pair that has at least one persisted
 * {@link HistoricalObservationEntity} - used by {@code HistoricalDelayProfileRefreshScheduler}
 * to discover which profiles need (re)computing, since there is no separate "trains to track"
 * registry. A JPQL projection type, not a domain model - intentionally lives in this package.
 */
public record TrainStationKey(String trainNumber, String stationCode) {
}
