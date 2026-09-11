package com.railpredictor.repository;

import com.railpredictor.model.domain.HistoricalObservation;
import org.springframework.stereotype.Component;

/**
 * Explicit mapping between the framework-free {@link HistoricalObservation} domain record and
 * the JPA {@link HistoricalObservationEntity} - the domain model must never depend on JPA, and
 * the entity must never leak outside the {@code repository} package's callers.
 */
@Component
public class HistoricalObservationEntityMapper {

    public HistoricalObservationEntity toEntity(HistoricalObservation observation) {
        return new HistoricalObservationEntity(
                observation.trainNumber(),
                observation.journeyDate(),
                observation.stationCode(),
                observation.stationSequence(),
                observation.scheduledArrival(),
                observation.actualArrival(),
                observation.scheduledDeparture(),
                observation.actualDeparture(),
                observation.arrivalDelayMinutes(),
                observation.departureDelayMinutes(),
                observation.observedAt(),
                observation.source());
    }

    public HistoricalObservation toDomain(HistoricalObservationEntity entity) {
        return new HistoricalObservation(
                entity.getTrainNumber(),
                entity.getJourneyDate(),
                entity.getStationCode(),
                entity.getStationSequence(),
                entity.getScheduledArrival(),
                entity.getActualArrival(),
                entity.getScheduledDeparture(),
                entity.getActualDeparture(),
                entity.getArrivalDelayMinutes(),
                entity.getDepartureDelayMinutes(),
                entity.getObservedAt(),
                entity.getSource());
    }
}
