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
 *
 * <p>{@code predictedNextStationDelayMinutes} (Phase 21) is the separate, next-station-scoped
 * prediction - the one actually evaluated for accuracy (see {@code PredictionSnapshot}) - never to
 * be confused with {@code predictedTotalDelayMinutes}/{@code predictedEta}, which remain
 * destination-scoped. Both legitimately reuse the same disruption-impact contribution; only the
 * historical term differs in scope between them (see docs/prediction-model.md's Phase 21 notes).
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
        Instant predictedEta,
        int predictedNextStationDelayMinutes) {

    /** Pre-Phase-21 shape, preserved so existing callers/tests need not change: defaults
     * {@code predictedNextStationDelayMinutes} to {@code currentDelayMinutes +
     * predictedExtraDelayMinutes} - correct for any caller not concerned with the next-station-
     * scoped historical/disruption contribution. */
    public PredictionBreakdownResponse(
            double baseTravelTimeMinutes,
            int currentDelayMinutes,
            int predictedExtraDelayMinutes,
            int historicalAdjustmentMinutes,
            HistoricalAdjustmentSource historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            int recoveryMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta) {
        this(baseTravelTimeMinutes, currentDelayMinutes, predictedExtraDelayMinutes, historicalAdjustmentMinutes,
                historicalAdjustmentSource, historicalAdjustmentProvenance, recoveryMinutes, predictedTotalDelayMinutes,
                predictedEta, currentDelayMinutes + predictedExtraDelayMinutes);
    }
}
