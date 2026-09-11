package com.railpredictor.repository;

import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import org.springframework.stereotype.Component;

/**
 * Explicit mapping between the framework-free {@link HistoricalSectionDelayProfile} domain
 * record and the JPA {@link HistoricalSectionDelayProfileEntity} - mirrors
 * {@link HistoricalDelayProfileEntityMapper}'s role for the station-level profile.
 */
@Component
public class HistoricalSectionDelayProfileEntityMapper {

    public HistoricalSectionDelayProfileEntity toEntity(HistoricalSectionDelayProfile profile) {
        return new HistoricalSectionDelayProfileEntity(
                profile.trainNumber(),
                profile.fromStationCode(),
                profile.toStationCode(),
                profile.sampleCount(),
                profile.averageDelayChangeMinutes(),
                profile.medianDelayChangeMinutes(),
                profile.standardDeviationMinutes(),
                profile.source(),
                profile.computedAt());
    }

    public HistoricalSectionDelayProfile toDomain(HistoricalSectionDelayProfileEntity entity) {
        return new HistoricalSectionDelayProfile(
                entity.getTrainNumber(),
                entity.getFromStationCode(),
                entity.getToStationCode(),
                entity.getSampleCount(),
                entity.getAverageDelayChangeMinutes(),
                entity.getMedianDelayChangeMinutes(),
                entity.getStandardDeviationMinutes(),
                entity.getSource(),
                entity.getComputedAt());
    }
}
