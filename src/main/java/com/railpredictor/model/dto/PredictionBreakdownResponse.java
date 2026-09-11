package com.railpredictor.model.dto;

import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import java.time.Instant;

/**
 * The delay/ETA breakdown. {@code baseTravelTimeMinutes} is derived (remaining distance/speed);
 * {@code currentDelayMinutes} is real (from RailRadar); {@code predictedExtraDelayMinutes} and
 * {@code recoveryMinutes} are simulated; {@code historicalAdjustmentMinutes} is derived from
 * historical data (real when available, an assumption of 0 otherwise); {@code predictedEta} and
 * {@code predictedTotalDelayMinutes} are the final derived outputs. See
 * docs/prediction-model.md for the exact formula and docs/architecture.md for what "real" vs
 * "derived" vs "simulated" mean here.
 *
 * <p>{@code historicalAdjustmentSource} (Phase 16H-3) is one of {@code "SECTION"},
 * {@code "STATION_FALLBACK"}, or {@code "NONE"} - which strategy actually produced
 * {@code historicalAdjustmentMinutes}, never left for a client to infer from {@code warnings}
 * text. {@code historicalAdjustmentProvenance} is where that winning strategy's data came from
 * (e.g. {@code "railradar"}, {@code "mock-provider"}, {@code "unavailable"}, or an explicit
 * {@code "mixed(...)"} combination) - a genuinely separate axis from {@code source}, never
 * conflated with it (a {@code SECTION} adjustment can be mock-sourced exactly as easily as a
 * {@code STATION_FALLBACK} one).
 */
public record PredictionBreakdownResponse(
        double baseTravelTimeMinutes,
        int currentDelayMinutes,
        int predictedExtraDelayMinutes,
        int historicalAdjustmentMinutes,
        HistoricalAdjustmentSource historicalAdjustmentSource,
        String historicalAdjustmentProvenance,
        int recoveryMinutes,
        int predictedTotalDelayMinutes,
        Instant predictedEta) {
}
