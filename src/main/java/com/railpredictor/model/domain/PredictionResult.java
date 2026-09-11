package com.railpredictor.model.domain;

import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import java.time.Instant;
import java.util.List;

/**
 * The final prediction for one train. {@code historicalAdjustmentMinutes} may be positive or
 * negative (it adjusts the estimate, it isn't itself a raw delay); every other delay-shaped field
 * is non-negative. {@code warnings} carries assumptions and degraded-input notices so the output
 * never presents simulated/mocked/assumed data as if it were measured.
 *
 * <p>{@code historicalAdjustmentResolution} (Phase 16H-3) makes explicit which of two
 * fundamentally different strategies actually produced {@code historicalAdjustmentMinutes} - see
 * {@link HistoricalAdjustmentResolution}'s own Javadoc.
 *
 * <p>{@code weatherProvenance} (Phase 17) is {@code WeatherData.source()} at the moment this
 * prediction was made (e.g. {@code "mock-provider"}, {@code "open-meteo"}), or {@code null} when
 * weather was unavailable for this request - a lightweight audit trail so a later evaluation can
 * determine whether a given prediction was made with mock, real, or no weather data at all (see
 * {@code PredictionSnapshot}). Deliberately just the source string, not the full
 * {@code WeatherData} - the REST output already surfaces weather availability via
 * {@code confidence.contributingFactors}/{@code warnings}, so duplicating it here as a new public
 * field is unnecessary; this exists for internal audit only.
 *
 * <p>{@code disruptionImpactAssessment} (Phase 19) is the rolled-up translation of every
 * currently-active real/mock {@code RailwayDisruption} on the current section into a delay
 * contribution - see {@link DisruptionImpactAssessment}'s own Javadoc. Its
 * {@code additionalDelayMinutes} (when non-null) is already included in
 * {@code predictedTotalDelayMinutes} and {@code predictedEta} exactly once, as its own distinct
 * term alongside {@code historicalAdjustmentMinutes} - never re-applied through
 * {@code predictedExtraDelayMinutes} (the simulated contribution) or {@code recoveryMinutes}. See
 * {@code PredictionEngine} for exactly where this term is added, and
 * {@code disruptionimpact.SimulationSuppression} for how the corresponding simulated
 * {@code DisruptionModel} is suppressed whenever a real disruption of the same kind is active, so
 * the two are never additive for the same underlying cause.
 */
public record PredictionResult(
        String trainNumber,
        String trainName,
        TrainStatus status,
        Station currentStation,
        Station nextStation,
        int currentDelayMinutes,
        double distanceFromOriginKm,
        Double remainingDistanceKm,
        Double estimatedSpeedKmh,
        SectionType sectionType,
        double baseTravelTimeMinutes,
        int predictedExtraDelayMinutes,
        int historicalAdjustmentMinutes,
        int recoveryMinutes,
        int predictedTotalDelayMinutes,
        Instant predictedEta,
        ConfidenceScore confidence,
        List<DisruptionResult> disruptions,
        List<CascadeEffect> cascadeEffects,
        List<String> warnings,
        HistoricalAdjustmentResolution historicalAdjustmentResolution,
        String weatherProvenance,
        DisruptionImpactAssessment disruptionImpactAssessment) {

    /** Pre-Phase-19 shape, preserved so existing callers need not change: defaults
     * {@code disruptionImpactAssessment} to {@link DisruptionImpactAssessment#unavailable()} -
     * correct for any caller not concerned with real-disruption audit (no real disruption data was
     * consulted). */
    public PredictionResult(
            String trainNumber,
            String trainName,
            TrainStatus status,
            Station currentStation,
            Station nextStation,
            int currentDelayMinutes,
            double distanceFromOriginKm,
            Double remainingDistanceKm,
            Double estimatedSpeedKmh,
            SectionType sectionType,
            double baseTravelTimeMinutes,
            int predictedExtraDelayMinutes,
            int historicalAdjustmentMinutes,
            int recoveryMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            ConfidenceScore confidence,
            List<DisruptionResult> disruptions,
            List<CascadeEffect> cascadeEffects,
            List<String> warnings,
            HistoricalAdjustmentResolution historicalAdjustmentResolution,
            String weatherProvenance) {
        this(trainNumber, trainName, status, currentStation, nextStation, currentDelayMinutes,
                distanceFromOriginKm, remainingDistanceKm, estimatedSpeedKmh, sectionType,
                baseTravelTimeMinutes, predictedExtraDelayMinutes, historicalAdjustmentMinutes,
                recoveryMinutes, predictedTotalDelayMinutes, predictedEta, confidence, disruptions,
                cascadeEffects, warnings, historicalAdjustmentResolution, weatherProvenance,
                DisruptionImpactAssessment.unavailable());
    }

    /** Pre-Phase-17 shape, preserved so existing callers need not change: {@code weatherProvenance}
     * defaults to {@code null} - correct for any caller not concerned with weather-source audit. */
    public PredictionResult(
            String trainNumber,
            String trainName,
            TrainStatus status,
            Station currentStation,
            Station nextStation,
            int currentDelayMinutes,
            double distanceFromOriginKm,
            Double remainingDistanceKm,
            Double estimatedSpeedKmh,
            SectionType sectionType,
            double baseTravelTimeMinutes,
            int predictedExtraDelayMinutes,
            int historicalAdjustmentMinutes,
            int recoveryMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            ConfidenceScore confidence,
            List<DisruptionResult> disruptions,
            List<CascadeEffect> cascadeEffects,
            List<String> warnings,
            HistoricalAdjustmentResolution historicalAdjustmentResolution) {
        this(trainNumber, trainName, status, currentStation, nextStation, currentDelayMinutes,
                distanceFromOriginKm, remainingDistanceKm, estimatedSpeedKmh, sectionType,
                baseTravelTimeMinutes, predictedExtraDelayMinutes, historicalAdjustmentMinutes,
                recoveryMinutes, predictedTotalDelayMinutes, predictedEta, confidence, disruptions,
                cascadeEffects, warnings, historicalAdjustmentResolution, null);
    }

    /** Pre-Phase-16H-3 shape, preserved so existing callers need not change: defaults to
     * {@code NONE}/{@code UNAVAILABLE} - callers not concerned with historical-source transparency
     * are unaffected, but this default must never be mistaken for a real resolution. */
    public PredictionResult(
            String trainNumber,
            String trainName,
            TrainStatus status,
            Station currentStation,
            Station nextStation,
            int currentDelayMinutes,
            double distanceFromOriginKm,
            Double remainingDistanceKm,
            Double estimatedSpeedKmh,
            SectionType sectionType,
            double baseTravelTimeMinutes,
            int predictedExtraDelayMinutes,
            int historicalAdjustmentMinutes,
            int recoveryMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            ConfidenceScore confidence,
            List<DisruptionResult> disruptions,
            List<CascadeEffect> cascadeEffects,
            List<String> warnings) {
        this(trainNumber, trainName, status, currentStation, nextStation, currentDelayMinutes,
                distanceFromOriginKm, remainingDistanceKm, estimatedSpeedKmh, sectionType,
                baseTravelTimeMinutes, predictedExtraDelayMinutes, historicalAdjustmentMinutes,
                recoveryMinutes, predictedTotalDelayMinutes, predictedEta, confidence, disruptions,
                cascadeEffects, warnings,
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE));
    }

    public PredictionResult {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        trainName = Guard.requireNonBlank(trainName, "trainName");
        status = Guard.requireNonNull(status, "status");
        currentStation = Guard.requireNonNull(currentStation, "currentStation");
        Guard.requireNonNegative(currentDelayMinutes, "currentDelayMinutes");
        Guard.requireNonNegative(distanceFromOriginKm, "distanceFromOriginKm");
        if (remainingDistanceKm != null) {
            Guard.requireNonNegative(remainingDistanceKm, "remainingDistanceKm");
        }
        if (estimatedSpeedKmh != null) {
            Guard.requireNonNegative(estimatedSpeedKmh, "estimatedSpeedKmh");
        }
        sectionType = Guard.requireNonNull(sectionType, "sectionType");
        Guard.requireNonNegative(baseTravelTimeMinutes, "baseTravelTimeMinutes");
        Guard.requireNonNegative(predictedExtraDelayMinutes, "predictedExtraDelayMinutes");
        Guard.requireNonNegative(recoveryMinutes, "recoveryMinutes");
        Guard.requireNonNegative(predictedTotalDelayMinutes, "predictedTotalDelayMinutes");
        predictedEta = Guard.requireNonNull(predictedEta, "predictedEta");
        confidence = Guard.requireNonNull(confidence, "confidence");
        disruptions = List.copyOf(Guard.requireNonNull(disruptions, "disruptions"));
        cascadeEffects = List.copyOf(Guard.requireNonNull(cascadeEffects, "cascadeEffects"));
        warnings = List.copyOf(Guard.requireNonNull(warnings, "warnings"));
        historicalAdjustmentResolution = Guard.requireNonNull(historicalAdjustmentResolution, "historicalAdjustmentResolution");
        disruptionImpactAssessment = Guard.requireNonNull(disruptionImpactAssessment, "disruptionImpactAssessment");
    }
}
