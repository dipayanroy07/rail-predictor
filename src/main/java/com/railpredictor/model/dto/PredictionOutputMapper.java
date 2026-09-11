package com.railpredictor.model.dto;

import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.Station;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Translates the internal {@link PredictionResult} into the external {@link PredictionOutput}
 * JSON shape. This is the one place that shape is decided - callers (a future REST controller,
 * the JSON-generation test in this phase) never build {@link PredictionOutput} by hand.
 */
@Component
public class PredictionOutputMapper {

    public PredictionOutput toOutput(PredictionResult result) {
        Objects.requireNonNull(result, "result");

        // Only what actually triggered - the full 6-model evaluation (including "didn't fire"
        // results) is an internal simulation detail, not something a frontend needs to render.
        List<DisruptionResponse> triggeredDisruptions = result.disruptions().stream()
                .filter(DisruptionResult::triggered)
                .map(d -> new DisruptionResponse(d.type(), d.delayMinutes(), d.description()))
                .toList();

        List<CascadeEffectResponse> cascadeEffects = result.cascadeEffects().stream()
                .map(c -> new CascadeEffectResponse(c.description(), c.depth(), c.additionalDelayMinutes()))
                .toList();

        PredictionBreakdownResponse prediction = new PredictionBreakdownResponse(
                result.baseTravelTimeMinutes(),
                result.currentDelayMinutes(),
                result.predictedExtraDelayMinutes(),
                result.historicalAdjustmentMinutes(),
                result.historicalAdjustmentResolution().source(),
                result.historicalAdjustmentResolution().provenance(),
                result.recoveryMinutes(),
                result.predictedTotalDelayMinutes(),
                result.predictedEta(),
                result.predictedNextStationDelayMinutes());

        SimulationResponse simulation = new SimulationResponse(triggeredDisruptions, cascadeEffects, result.recoveryMinutes());

        ConfidenceResponse confidence = new ConfidenceResponse(
                result.confidence().score(),
                result.confidence().level(),
                result.confidence().contributingFactors(),
                result.confidence().warnings());

        return new PredictionOutput(
                result.trainNumber(),
                result.trainName(),
                result.status(),
                result.currentDelayMinutes(),
                toStationResponse(result.currentStation()),
                toStationResponse(result.nextStation()),
                result.distanceFromOriginKm(),
                result.remainingDistanceKm(),
                result.estimatedSpeedKmh(),
                result.sectionType(),
                prediction,
                simulation,
                confidence,
                result.warnings());
    }

    private static StationResponse toStationResponse(Station station) {
        if (station == null) {
            return null;
        }
        return new StationResponse(station.code(), station.name(), station.latitude(), station.longitude());
    }
}
