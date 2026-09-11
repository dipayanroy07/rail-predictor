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
 * The JPA persistence model for one row of {@code historical_section_delay_profiles} (see
 * V3__create_historical_section_delay_profiles.sql) - a cached, reproducible aggregate over
 * {@link HistoricalObservationEntity} rows, never itself the source of truth. Deliberately
 * separate from {@link com.railpredictor.model.domain.HistoricalSectionDelayProfile} (the
 * framework-free domain record) - see {@link HistoricalSectionDelayProfileEntityMapper}. Mirrors
 * {@link HistoricalDelayProfileEntity}'s own pattern exactly, keyed on a (from, to) pair instead
 * of a single station.
 */
@Entity
@Table(name = "historical_section_delay_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_historical_section_delay_profile",
                columnNames = {"train_number", "from_station_code", "to_station_code"}))
public class HistoricalSectionDelayProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 10)
    private String trainNumber;

    @Column(name = "from_station_code", nullable = false, length = 10)
    private String fromStationCode;

    @Column(name = "to_station_code", nullable = false, length = 10)
    private String toStationCode;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "average_delay_change_minutes", nullable = false)
    private double averageDelayChangeMinutes;

    @Column(name = "median_delay_change_minutes", nullable = false)
    private double medianDelayChangeMinutes;

    @Column(name = "standard_deviation_minutes", nullable = false)
    private double standardDeviationMinutes;

    @Column(name = "source", nullable = false, length = 200)
    private String source;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected HistoricalSectionDelayProfileEntity() {
        // required by JPA
    }

    public HistoricalSectionDelayProfileEntity(
            String trainNumber,
            String fromStationCode,
            String toStationCode,
            int sampleCount,
            double averageDelayChangeMinutes,
            double medianDelayChangeMinutes,
            double standardDeviationMinutes,
            String source,
            Instant computedAt) {
        this.trainNumber = trainNumber;
        this.fromStationCode = fromStationCode;
        this.toStationCode = toStationCode;
        this.sampleCount = sampleCount;
        this.averageDelayChangeMinutes = averageDelayChangeMinutes;
        this.medianDelayChangeMinutes = medianDelayChangeMinutes;
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

    public String getFromStationCode() {
        return fromStationCode;
    }

    public String getToStationCode() {
        return toStationCode;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public double getAverageDelayChangeMinutes() {
        return averageDelayChangeMinutes;
    }

    public double getMedianDelayChangeMinutes() {
        return medianDelayChangeMinutes;
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
    public void updateFrom(HistoricalSectionDelayProfileEntity latest) {
        this.sampleCount = latest.sampleCount;
        this.averageDelayChangeMinutes = latest.averageDelayChangeMinutes;
        this.medianDelayChangeMinutes = latest.medianDelayChangeMinutes;
        this.standardDeviationMinutes = latest.standardDeviationMinutes;
        this.source = latest.source;
        this.computedAt = latest.computedAt;
    }
}
