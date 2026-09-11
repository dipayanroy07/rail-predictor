package com.railpredictor.repository;

import com.railpredictor.model.domain.HistoricalDelayProfile;
import org.springframework.stereotype.Component;

/**
 * Explicit mapping between the framework-free {@link HistoricalDelayProfile} domain record and
 * the JPA {@link HistoricalDelayProfileEntity} - mirrors
 * {@link HistoricalObservationEntityMapper}'s role for observations.
 */
@Component
public class HistoricalDelayProfileEntityMapper {

    public HistoricalDelayProfileEntity toEntity(HistoricalDelayProfile profile) {
        return new HistoricalDelayProfileEntity(
                profile.trainNumber(),
                profile.stationCode(),
                profile.sampleCount(),
                profile.averageArrivalDelayMinutes(),
                profile.medianArrivalDelayMinutes(),
                profile.standardDeviationMinutes(),
                profile.source(),
                profile.computedAt());
    }

    public HistoricalDelayProfile toDomain(HistoricalDelayProfileEntity entity) {
        return new HistoricalDelayProfile(
                entity.getTrainNumber(),
                entity.getStationCode(),
                entity.getSampleCount(),
                entity.getAverageArrivalDelayMinutes(),
                entity.getMedianArrivalDelayMinutes(),
                entity.getStandardDeviationMinutes(),
                entity.getSource(),
                entity.getComputedAt());
    }
}
