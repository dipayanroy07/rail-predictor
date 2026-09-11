package com.railpredictor.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The JPA persistence model for one row of {@code historical_observations} (see
 * V1__create_historical_observations.sql). Deliberately separate from
 * {@link com.railpredictor.model.domain.HistoricalObservation} (the framework-free domain
 * record) - see {@link HistoricalObservationEntityMapper} for the explicit mapping between them.
 * A plain mutable class, not a record: JPA entities need a no-arg constructor and mutable fields
 * for Hibernate to manage.
 */
@Entity
@Table(name = "historical_observations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_historical_observation",
                columnNames = {"train_number", "journey_date", "station_code", "station_sequence"}))
public class HistoricalObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 10)
    private String trainNumber;

    @Column(name = "journey_date", nullable = false)
    private LocalDate journeyDate;

    @Column(name = "station_code", nullable = false, length = 10)
    private String stationCode;

    @Column(name = "station_sequence")
    private Integer stationSequence;

    @Column(name = "scheduled_arrival", length = 50)
    private String scheduledArrival;

    @Column(name = "actual_arrival", length = 50)
    private String actualArrival;

    @Column(name = "scheduled_departure", length = 50)
    private String scheduledDeparture;

    @Column(name = "actual_departure", length = 50)
    private String actualDeparture;

    @Column(name = "arrival_delay_minutes")
    private Integer arrivalDelayMinutes;

    @Column(name = "departure_delay_minutes")
    private Integer departureDelayMinutes;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    protected HistoricalObservationEntity() {
        // required by JPA
    }

    public HistoricalObservationEntity(
            String trainNumber,
            LocalDate journeyDate,
            String stationCode,
            Integer stationSequence,
            String scheduledArrival,
            String actualArrival,
            String scheduledDeparture,
            String actualDeparture,
            Integer arrivalDelayMinutes,
            Integer departureDelayMinutes,
            Instant observedAt,
            String source) {
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        this.stationCode = stationCode;
        this.stationSequence = stationSequence;
        this.scheduledArrival = scheduledArrival;
        this.actualArrival = actualArrival;
        this.scheduledDeparture = scheduledDeparture;
        this.actualDeparture = actualDeparture;
        this.arrivalDelayMinutes = arrivalDelayMinutes;
        this.departureDelayMinutes = departureDelayMinutes;
        this.observedAt = observedAt;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public LocalDate getJourneyDate() {
        return journeyDate;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Integer getStationSequence() {
        return stationSequence;
    }

    public String getScheduledArrival() {
        return scheduledArrival;
    }

    public String getActualArrival() {
        return actualArrival;
    }

    public String getScheduledDeparture() {
        return scheduledDeparture;
    }

    public String getActualDeparture() {
        return actualDeparture;
    }

    public Integer getArrivalDelayMinutes() {
        return arrivalDelayMinutes;
    }

    public Integer getDepartureDelayMinutes() {
        return departureDelayMinutes;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getSource() {
        return source;
    }

    /** Applies a freshly-observed reading onto an existing (already-persisted) row - the "update"
     * half of the upsert; see {@code HistoricalObservationRecorder}. */
    public void updateFrom(HistoricalObservationEntity latest) {
        this.scheduledArrival = latest.scheduledArrival;
        this.actualArrival = latest.actualArrival;
        this.scheduledDeparture = latest.scheduledDeparture;
        this.actualDeparture = latest.actualDeparture;
        this.arrivalDelayMinutes = latest.arrivalDelayMinutes;
        this.departureDelayMinutes = latest.departureDelayMinutes;
        this.observedAt = latest.observedAt;
        this.source = latest.source;
    }
}
