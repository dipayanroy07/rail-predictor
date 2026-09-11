package com.railpredictor.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The JPA persistence model for one row of {@code prediction_snapshots} (see
 * V4__create_prediction_snapshots.sql) - deliberately separate from
 * {@link com.railpredictor.model.domain.PredictionSnapshot} (the framework-free domain record) -
 * see {@link PredictionSnapshotEntityMapper}. A plain mutable class, not a record: JPA entities
 * need a no-arg constructor and mutable fields for Hibernate to manage.
 *
 * <p>Unlike {@link HistoricalObservationEntity}/{@link HistoricalDelayProfileEntity} (upserted in
 * place on every refresh), a snapshot is created once, immutable in its "predicted" fields
 * forever, and updated <em>only</em> by {@code PredictionEvaluationRefresher} filling in the
 * {@code actual*}/{@code error}/{@code evaluatedAt} columns once a matching observation appears.
 */
@Entity
@Table(name = "prediction_snapshots")
public class PredictionSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_number", nullable = false, length = 10)
    private String trainNumber;

    @Column(name = "prediction_made_at", nullable = false)
    private Instant predictionMadeAt;

    @Column(name = "target_station_code", nullable = false, length = 10)
    private String targetStationCode;

    @Column(name = "current_delay_minutes", nullable = false)
    private int currentDelayMinutes;

    @Column(name = "predicted_next_station_delay_minutes", nullable = false)
    private int predictedNextStationDelayMinutes;

    @Column(name = "predicted_total_delay_minutes", nullable = false)
    private int predictedTotalDelayMinutes;

    @Column(name = "predicted_eta", nullable = false)
    private Instant predictedEta;

    @Column(name = "historical_adjustment_minutes", nullable = false)
    private int historicalAdjustmentMinutes;

    @Column(name = "historical_adjustment_source", nullable = false, length = 20)
    private String historicalAdjustmentSource;

    @Column(name = "historical_adjustment_provenance", nullable = false, length = 200)
    private String historicalAdjustmentProvenance;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore;

    @Column(name = "evaluation_status", nullable = false, length = 30)
    private String evaluationStatus;

    @Column(name = "actual_delay_minutes")
    private Integer actualDelayMinutes;

    @Column(name = "error_minutes")
    private Integer errorMinutes;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    /** Phase 16H-7: which process produced this snapshot - see
     * {@link com.railpredictor.model.domain.PredictionEvaluationMode}. Stored as its {@code name()},
     * exactly like {@code evaluationStatus}/{@code historicalAdjustmentSource}. */
    @Column(name = "evaluation_mode", nullable = false, length = 20)
    private String evaluationMode;

    /** Phase 17: {@code WeatherData.source()} at prediction time, or {@code null} when weather was
     * unavailable - see {@link com.railpredictor.model.domain.PredictionResult#weatherProvenance()}. */
    @Column(name = "weather_provenance", length = 200)
    private String weatherProvenance;

    /** Phase 19: the real disruption-impact minutes actually added to
     * {@code predicted_total_delay_minutes}, or {@code null} when real disruption data was
     * unavailable/present-but-not-estimable - see
     * {@link com.railpredictor.model.domain.PredictionSnapshot#disruptionImpactMinutes()}. */
    @Column(name = "disruption_impact_minutes")
    private Integer disruptionImpactMinutes;

    /** Phase 21: the separate, next-station-scoped historical adjustment that genuinely
     * contributes to {@code predictedNextStationDelayMinutes} - see
     * {@link com.railpredictor.model.domain.PredictionSnapshot}'s own Javadoc. Never to be
     * confused with {@link #historicalAdjustmentMinutes}, which remains destination-scoped. */
    @Column(name = "next_station_historical_adjustment_minutes", nullable = false)
    private int nextStationHistoricalAdjustmentMinutes;

    @Column(name = "next_station_historical_adjustment_source", nullable = false, length = 20)
    private String nextStationHistoricalAdjustmentSource;

    @Column(name = "next_station_historical_adjustment_provenance", nullable = false, length = 200)
    private String nextStationHistoricalAdjustmentProvenance;

    /** Phase 21: simulation's own raw contribution, persisted directly for unambiguous future
     * ablation - see {@link com.railpredictor.model.domain.PredictionSnapshot}'s own Javadoc. */
    @Column(name = "predicted_extra_delay_minutes", nullable = false)
    private int predictedExtraDelayMinutes;

    /** Phase 22E: marks this snapshot's evaluation outcome as known-untrustworthy evidence,
     * without altering any other column - see
     * {@link com.railpredictor.model.domain.PredictionSnapshot}'s own Javadoc. */
    @Column(name = "quarantined", nullable = false)
    private boolean quarantined;

    @Column(name = "quarantine_reason", length = 500)
    private String quarantineReason;

    protected PredictionSnapshotEntity() {
        // required by JPA
    }

    /** Pre-Phase-16H-7 shape, preserved so existing callers/tests need not change: defaults
     * {@code evaluationMode} to {@code "LIVE_EVALUATION"} - correct for every snapshot ever
     * created before this phase, since no other mode was producible. */
    public PredictionSnapshotEntity(
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            String historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            String evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt) {
        this(trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                "LIVE_EVALUATION");
    }

    /** Pre-Phase-17 shape, preserved so existing callers/tests need not change: defaults
     * {@code weatherProvenance} to {@code null} - correct for every snapshot created before this
     * phase. */
    public PredictionSnapshotEntity(
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            String historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            String evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            String evaluationMode) {
        this(trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, null);
    }

    /** Pre-Phase-19 shape, preserved so existing callers/tests need not change: defaults
     * {@code disruptionImpactMinutes} to {@code null} - correct for every snapshot created before
     * this phase. */
    public PredictionSnapshotEntity(
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            String historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            String evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            String evaluationMode,
            String weatherProvenance) {
        this(trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, weatherProvenance, null);
    }

    /** Pre-Phase-21 shape, preserved so existing callers/tests need not change: defaults the three
     * new next-station-historical fields to {@code 0}/{@code "NONE"}/{@code "unavailable"}, and
     * {@code predictedExtraDelayMinutes} to {@code predictedNextStationDelayMinutes -
     * currentDelayMinutes} (exact for every row created before this phase - see
     * {@link com.railpredictor.model.domain.PredictionSnapshot}'s own backward-compatible
     * constructor for why). */
    public PredictionSnapshotEntity(
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            String historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            String evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            String evaluationMode,
            String weatherProvenance,
            Integer disruptionImpactMinutes) {
        this(trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, weatherProvenance, disruptionImpactMinutes,
                0, "NONE", "unavailable", predictedNextStationDelayMinutes - currentDelayMinutes);
    }

    /** Pre-Phase-22E shape, preserved so existing callers/tests need not change: defaults
     * {@code quarantined} to {@code false} and {@code quarantineReason} to {@code null}. */
    public PredictionSnapshotEntity(
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            String historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            String evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            String evaluationMode,
            String weatherProvenance,
            Integer disruptionImpactMinutes,
            int nextStationHistoricalAdjustmentMinutes,
            String nextStationHistoricalAdjustmentSource,
            String nextStationHistoricalAdjustmentProvenance,
            int predictedExtraDelayMinutes) {
        this(trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, weatherProvenance, disruptionImpactMinutes,
                nextStationHistoricalAdjustmentMinutes, nextStationHistoricalAdjustmentSource,
                nextStationHistoricalAdjustmentProvenance, predictedExtraDelayMinutes, false, null);
    }

    public PredictionSnapshotEntity(
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            String historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            String evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            String evaluationMode,
            String weatherProvenance,
            Integer disruptionImpactMinutes,
            int nextStationHistoricalAdjustmentMinutes,
            String nextStationHistoricalAdjustmentSource,
            String nextStationHistoricalAdjustmentProvenance,
            int predictedExtraDelayMinutes,
            boolean quarantined,
            String quarantineReason) {
        this.trainNumber = trainNumber;
        this.predictionMadeAt = predictionMadeAt;
        this.targetStationCode = targetStationCode;
        this.currentDelayMinutes = currentDelayMinutes;
        this.predictedNextStationDelayMinutes = predictedNextStationDelayMinutes;
        this.predictedTotalDelayMinutes = predictedTotalDelayMinutes;
        this.predictedEta = predictedEta;
        this.historicalAdjustmentMinutes = historicalAdjustmentMinutes;
        this.historicalAdjustmentSource = historicalAdjustmentSource;
        this.historicalAdjustmentProvenance = historicalAdjustmentProvenance;
        this.confidenceScore = confidenceScore;
        this.evaluationStatus = evaluationStatus;
        this.actualDelayMinutes = actualDelayMinutes;
        this.errorMinutes = errorMinutes;
        this.evaluatedAt = evaluatedAt;
        this.evaluationMode = evaluationMode;
        this.weatherProvenance = weatherProvenance;
        this.disruptionImpactMinutes = disruptionImpactMinutes;
        this.nextStationHistoricalAdjustmentMinutes = nextStationHistoricalAdjustmentMinutes;
        this.nextStationHistoricalAdjustmentSource = nextStationHistoricalAdjustmentSource;
        this.nextStationHistoricalAdjustmentProvenance = nextStationHistoricalAdjustmentProvenance;
        this.predictedExtraDelayMinutes = predictedExtraDelayMinutes;
        this.quarantined = quarantined;
        this.quarantineReason = quarantineReason;
    }

    public Long getId() {
        return id;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public Instant getPredictionMadeAt() {
        return predictionMadeAt;
    }

    public String getTargetStationCode() {
        return targetStationCode;
    }

    public int getCurrentDelayMinutes() {
        return currentDelayMinutes;
    }

    public int getPredictedNextStationDelayMinutes() {
        return predictedNextStationDelayMinutes;
    }

    public int getPredictedTotalDelayMinutes() {
        return predictedTotalDelayMinutes;
    }

    public Instant getPredictedEta() {
        return predictedEta;
    }

    public int getHistoricalAdjustmentMinutes() {
        return historicalAdjustmentMinutes;
    }

    public String getHistoricalAdjustmentSource() {
        return historicalAdjustmentSource;
    }

    public String getHistoricalAdjustmentProvenance() {
        return historicalAdjustmentProvenance;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public String getEvaluationStatus() {
        return evaluationStatus;
    }

    public Integer getActualDelayMinutes() {
        return actualDelayMinutes;
    }

    public Integer getErrorMinutes() {
        return errorMinutes;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public String getEvaluationMode() {
        return evaluationMode;
    }

    public String getWeatherProvenance() {
        return weatherProvenance;
    }

    public Integer getDisruptionImpactMinutes() {
        return disruptionImpactMinutes;
    }

    public int getNextStationHistoricalAdjustmentMinutes() {
        return nextStationHistoricalAdjustmentMinutes;
    }

    public String getNextStationHistoricalAdjustmentSource() {
        return nextStationHistoricalAdjustmentSource;
    }

    public String getNextStationHistoricalAdjustmentProvenance() {
        return nextStationHistoricalAdjustmentProvenance;
    }

    public int getPredictedExtraDelayMinutes() {
        return predictedExtraDelayMinutes;
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public String getQuarantineReason() {
        return quarantineReason;
    }

    /** Applies the evaluation outcome onto this (already-persisted) row - the only mutation a
     * snapshot ever undergoes after creation, until Phase 22E's quarantine mechanism. */
    public void applyEvaluation(String evaluationStatus, Integer actualDelayMinutes, Integer errorMinutes, Instant evaluatedAt) {
        this.evaluationStatus = evaluationStatus;
        this.actualDelayMinutes = actualDelayMinutes;
        this.errorMinutes = errorMinutes;
        this.evaluatedAt = evaluatedAt;
    }

    /** Phase 22E: marks this row as known-untrustworthy evidence - never touches any predicted or
     * observed value, only this pair. See {@link PredictionSnapshotQuarantineService}. */
    public void applyQuarantine(String reason) {
        this.quarantined = true;
        this.quarantineReason = reason;
    }
}
