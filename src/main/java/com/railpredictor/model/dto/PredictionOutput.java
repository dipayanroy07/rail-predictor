package com.railpredictor.model.dto;

import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import java.util.List;

/**
 * The complete, frontend-facing JSON shape for one train's prediction - deliberately its own
 * shape rather than serializing {@code PredictionResult} directly, so this contract doesn't
 * change just because the internal domain model does. Built by {@link PredictionOutputMapper}.
 *
 * <p>Fields are always present; unknown values are {@code null} rather than omitted, so the
 * shape stays stable for a frontend regardless of what data was available for a given train (see
 * docs/architecture.md on real vs derived vs simulated vs mocked vs assumed data).
 */
public record PredictionOutput(
        String trainNumber,
        String trainName,
        TrainStatus currentStatus,
        int currentDelayMinutes,
        StationResponse currentStation,
        StationResponse nextStation,
        double distanceFromOriginKm,
        Double remainingDistanceKm,
        Double estimatedSpeedKmh,
        SectionType estimatedSectionCondition,
        PredictionBreakdownResponse prediction,
        SimulationResponse simulation,
        ConfidenceResponse confidence,
        List<String> warnings) {
}
