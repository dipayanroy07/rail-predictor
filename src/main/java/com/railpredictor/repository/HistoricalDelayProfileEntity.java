package com.railpredictor.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * The JPA persistence model for one row of {@code historical_delay_profiles} (see
 * V2__create_historical_delay_profiles.sql) - a cached, reproducible aggregate over
 * {@link HistoricalObservationEntity} rows, never itself the source of truth. Deliberately
 * separate from {@link com.railpredictor.model.domain.HistoricalDelayProfile} (the
 * framework-free domain record) - see {@link HistoricalDelayProfileEntityMapper}.
 */
@Entity
@Table(name = "historical_delay_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_historical_delay_profile", columnNames = {"train_number", "station_code"}))
public class HistoricalDelayProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 10)
    private String trainNumber;

    @Column(name = "station_code", nullable = false, length = 10)
    private String stationCode;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "average_arrival_delay_minutes", nullable = false)
    private double averageArrivalDelayMinutes;

    @Column(name = "median_arrival_delay_minutes", nullable = false)
    private double medianArrivalDelayMinutes;

    @Column(name = "standard_deviation_minutes", nullable = false)
    private double standardDeviationMinutes;

    @Column(name = "source", nullable = false, length = 200)
    private String source;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected HistoricalDelayProfileEntity() {
        // required by JPA
    }

    public HistoricalDelayProfileEntity(
            String trainNumber,
            String stationCode,
            int sampleCount,
            double averageArrivalDelayMinutes,
            double medianArrivalDelayMinutes,
            double standardDeviationMinutes,
            String source,
            Instant computedAt) {
        this.trainNumber = trainNumber;
        this.stationCode = stationCode;
        this.sampleCount = sampleCount;
        this.averageArrivalDelayMinutes = averageArrivalDelayMinutes;
        this.medianArrivalDelayMinutes = medianArrivalDelayMinutes;
        this.standardDeviationMinutes = standardDeviationMinutes;
        this.source = source;
        this.computedAt = computedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getStationCode() {
        return stationCode;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public double getAverageArrivalDelayMinutes() {
        return averageArrivalDelayMinutes;
    }

    public double getMedianArrivalDelayMinutes() {
        return medianArrivalDelayMinutes;
    }

    public double getStandardDeviationMinutes() {
        return standardDeviationMinutes;
    }

    public String getSource() {
        return source;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    /** Replaces this row's statistics with a freshly re-aggregated computation - re-aggregation
     * updates the existing row in place, it never creates a second row for the same key. */
    public void updateFrom(HistoricalDelayProfileEntity latest) {
        this.sampleCount = latest.sampleCount;
        this.averageArrivalDelayMinutes = latest.averageArrivalDelayMinutes;
        this.medianArrivalDelayMinutes = latest.medianArrivalDelayMinutes;
        this.standardDeviationMinutes = latest.standardDeviationMinutes;
        this.source = latest.source;
        this.computedAt = latest.computedAt;
    }
}
