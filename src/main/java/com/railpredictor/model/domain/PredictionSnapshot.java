package com.railpredictor.model.domain;

import java.time.Instant;

/**
 * A durable record of one prediction, captured at the moment it was made, so a later process can
 * check it against what actually happened (Phase 16H-5) - {@code PredictionResult} itself is never
 * persisted (it's a per-request response value), so without this, "did the system predict
 * accurately" could never be answered after the fact.
 *
 * <p><b>Evaluation target (a deliberate scope decision):</b> this snapshot evaluates the train's
 * delay at its <em>next station</em> ({@code targetStationCode} - {@code section.toStation()} at
 * prediction time), not at the final destination. RailRadar exposes no reliable
 * whole-journey-completion signal (see docs/historical-data-design.md's Phase 16H-1 findings), so
 * a destination-level match could never be trusted to be evaluating the right event; the
 * next-station arrival, by contrast, is exactly what {@code HistoricalObservationRecorder} already
 * persists reliably every time RailRadar reports the train has reached it.
 *
 * <p><b>{@code predictedNextStationDelayMinutes} is deliberately NOT
 * {@code PredictionResult.predictedTotalDelayMinutes()}.</b> The latter is scoped to the
 * *destination* and includes the historical adjustment, whose spatial scope (for a
 * {@code SECTION}-sourced adjustment) spans the *entire* remaining route, not just the next
 * station - including it here would over-attribute a route-wide signal to a single-station
 * outcome. {@code predictedNextStationDelayMinutes = currentDelayMinutes + predictedExtraDelayMinutes}
 * instead - the only two components of the existing formula whose scope reliably matches "just
 * the current section, ending at the next station" regardless of which historical strategy was
 * used. {@code predictedTotalDelayMinutes}/{@code predictedEta} are still captured here too, for
 * audit/reference, but are not themselves evaluated by this phase.
 *
 * <p>{@code actualDelayMinutes}/{@code errorMinutes}/{@code evaluatedAt} are {@code null} exactly
 * when {@code evaluationStatus} is {@link PredictionEvaluationStatus#PENDING} or
 * {@link PredictionEvaluationStatus#NOT_EVALUABLE}, and all three are non-null exactly when it is
 * {@link PredictionEvaluationStatus#EVALUATED_EXACT} or
 * {@link PredictionEvaluationStatus#EVALUATED_APPROXIMATE} - enforced by this record's own
 * constructor.
 *
 * <p>{@code errorMinutes = predictedNextStationDelayMinutes - actualDelayMinutes}, signed and
 * never clamped - see {@code PredictionAccuracyCalculator} for how this is aggregated.
 *
 * <p>{@code id} is {@code null} before persistence (assigned by the database) - mirrors every
 * other JPA-backed domain/entity pairing in this codebase.
 *
 * <p>{@code evaluationMode} (Phase 16H-7) records which process produced this snapshot -
 * {@link PredictionEvaluationMode#LIVE_EVALUATION} for every snapshot any code in this application
 * currently creates (see {@code PredictionSnapshotRecorder}), or the reserved-but-not-yet-producible
 * {@link PredictionEvaluationMode#HISTORICAL_BACKTEST}. Captured once, at creation, exactly like
 * {@code historicalAdjustmentSource} - never recomputed or assumed later; see that enum's own
 * Javadoc and docs/historical-data-design.md's Phase 16H-7 notes for the full LIVE-vs-BACKTEST
 * distinction and why true historical replay is not yet possible.
 *
 * <p>{@code weatherProvenance} (Phase 17) mirrors {@code PredictionResult.weatherProvenance()} -
 * {@code null} when weather was unavailable for this prediction, otherwise the weather source
 * string (e.g. {@code "mock-provider"}, {@code "open-meteo"}) captured at prediction time. This is
 * an audit field only - no accuracy-report breakdown by weather provenance exists yet (deliberately
 * out of scope; see docs/prediction-model.md's Phase 17 notes), but the data is preserved so a
 * future phase can add one without needing to backfill anything.
 *
 * <p>{@code disruptionImpactMinutes} (Phase 19) is
 * {@code PredictionResult.disruptionImpactAssessment().additionalDelayMinutes()} at prediction
 * time - {@code null} when real disruption data was unavailable or present-but-not-estimable,
 * otherwise the (possibly {@code 0}) minutes actually added to {@code predictedTotalDelayMinutes}.
 * This is the calibration-foundation field: it lets a future evaluation compare accuracy for
 * predictions made with a nonzero real-disruption contribution against those without one - see
 * docs/prediction-model.md's Phase 19 notes. No such comparison is built yet (no accuracy-report
 * breakdown by this field exists), and none is claimed to be calibrated - see
 * {@code CalibrationStatus}.
 *
 * <p>{@code nextStationHistoricalAdjustmentMinutes}/{@code nextStationHistoricalAdjustmentSource}/
 * {@code nextStationHistoricalAdjustmentProvenance} (Phase 21) are the separate, next-station-
 * scoped counterpart to {@code historicalAdjustmentMinutes}/{@code historicalAdjustmentSource}/
 * {@code historicalAdjustmentProvenance} (which remain destination-scoped, unchanged in meaning -
 * see {@code PredictionResult}'s own Javadoc). <b>This is the field that genuinely entered
 * {@code predictedNextStationDelayMinutes}</b> - before Phase 21, neither the destination-scoped
 * nor any next-station-scoped historical value was included in {@code predictedNextStationDelayMinutes}
 * at all (it was {@code currentDelayMinutes + predictedExtraDelayMinutes} only), which is exactly
 * the structural gap Phase 20 found. See docs/prediction-model.md's Phase 21 notes.
 *
 * <p>{@code predictedExtraDelayMinutes} (Phase 21) is simulation's own raw contribution -
 * {@code PredictionResult.predictedExtraDelayMinutes()} at prediction time, persisted directly so
 * a future ablation can isolate "with vs. without simulation" exactly
 * ({@code predictedNextStationDelayMinutes - currentDelayMinutes - nextStationHistoricalAdjustmentMinutes
 * - (disruptionImpactMinutes or 0)} would otherwise be ambiguous whenever the sum was clamped at
 * 0 - see {@code PredictionEngine}). Always non-negative (simulation's own contribution is never
 * itself negative - see {@code DelayCalculator}).
 *
 * <p>{@code quarantined}/{@code quarantineReason} (Phase 22E) mark a snapshot's evaluation outcome
 * as known-untrustworthy evidence <b>without deleting or altering anything else</b> -
 * {@code actualDelayMinutes}/{@code errorMinutes}/{@code evaluatedAt} and every predicted field
 * remain exactly what was originally computed/observed; only this pair says "do not count this as
 * valid evaluation evidence." {@code quarantineReason} is required (non-blank) exactly when
 * {@code quarantined} is {@code true}, and must be {@code null} otherwise - mirroring this
 * record's own {@code actualDelayMinutes}/{@code evaluationStatus} consistency pattern. See
 * docs/historical-data-design.md's Phase 22E notes for why this exists (the Phase 22D RailRadar
 * "upcoming"-placeholder finding) and every accuracy/calibration consumer that excludes quarantined
 * rows from valid-evidence counts.
 */
public record PredictionSnapshot(
        Long id,
        String trainNumber,
        Instant predictionMadeAt,
        String targetStationCode,
        int currentDelayMinutes,
        int predictedNextStationDelayMinutes,
        int predictedTotalDelayMinutes,
        Instant predictedEta,
        int historicalAdjustmentMinutes,
        HistoricalAdjustmentSource historicalAdjustmentSource,
        String historicalAdjustmentProvenance,
        double confidenceScore,
        PredictionEvaluationStatus evaluationStatus,
        Integer actualDelayMinutes,
        Integer errorMinutes,
        Instant evaluatedAt,
        PredictionEvaluationMode evaluationMode,
        String weatherProvenance,
        Integer disruptionImpactMinutes,
        int nextStationHistoricalAdjustmentMinutes,
        HistoricalAdjustmentSource nextStationHistoricalAdjustmentSource,
        String nextStationHistoricalAdjustmentProvenance,
        int predictedExtraDelayMinutes,
        boolean quarantined,
        String quarantineReason) {

    /** Pre-Phase-22E shape, preserved so existing callers/tests need not change: defaults
     * {@code quarantined} to {@code false} and {@code quarantineReason} to {@code null} - correct
     * for every snapshot created before this phase. */
    public PredictionSnapshot(
            Long id,
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            HistoricalAdjustmentSource historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            PredictionEvaluationStatus evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            PredictionEvaluationMode evaluationMode,
            String weatherProvenance,
            Integer disruptionImpactMinutes,
            int nextStationHistoricalAdjustmentMinutes,
            HistoricalAdjustmentSource nextStationHistoricalAdjustmentSource,
            String nextStationHistoricalAdjustmentProvenance,
            int predictedExtraDelayMinutes) {
        this(id, trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, weatherProvenance, disruptionImpactMinutes,
                nextStationHistoricalAdjustmentMinutes, nextStationHistoricalAdjustmentSource,
                nextStationHistoricalAdjustmentProvenance, predictedExtraDelayMinutes, false, null);
    }

    /** Pre-Phase-21 shape, preserved so existing callers/tests need not change: defaults the new
     * historical fields to {@code 0}/{@code NONE}/{@code UNAVAILABLE} and
     * {@code predictedExtraDelayMinutes} to {@code predictedNextStationDelayMinutes -
     * currentDelayMinutes} (exact for every snapshot created before this phase, since the old
     * formula was exactly {@code currentDelayMinutes + predictedExtraDelayMinutes}, clamping never
     * having been a factor because both addends were already non-negative). */
    public PredictionSnapshot(
            Long id,
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            HistoricalAdjustmentSource historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            PredictionEvaluationStatus evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            PredictionEvaluationMode evaluationMode,
            String weatherProvenance,
            Integer disruptionImpactMinutes) {
        this(id, trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, weatherProvenance, disruptionImpactMinutes,
                0, HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE,
                predictedNextStationDelayMinutes - currentDelayMinutes);
    }

    /** Pre-Phase-19 shape, preserved so existing callers/tests need not change: defaults
     * {@code disruptionImpactMinutes} to {@code null} - correct for every snapshot created before
     * this phase (real-disruption audit did not exist yet). */
    public PredictionSnapshot(
            Long id,
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            HistoricalAdjustmentSource historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            PredictionEvaluationStatus evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            PredictionEvaluationMode evaluationMode,
            String weatherProvenance) {
        this(id, trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, weatherProvenance, null);
    }

    /** Pre-Phase-17 shape, preserved so existing callers/tests need not change: defaults
     * {@code weatherProvenance} to {@code null} - correct for every snapshot created before this
     * phase (weather-source audit did not exist yet). */
    public PredictionSnapshot(
            Long id,
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            HistoricalAdjustmentSource historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            PredictionEvaluationStatus evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt,
            PredictionEvaluationMode evaluationMode) {
        this(id, trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                evaluationMode, null);
    }

    /** Pre-Phase-16H-7 shape, preserved so existing callers/tests need not change: defaults
     * {@code evaluationMode} to {@link PredictionEvaluationMode#LIVE_EVALUATION} - correct for
     * every snapshot ever created before this phase, since no other mode was producible. */
    public PredictionSnapshot(
            Long id,
            String trainNumber,
            Instant predictionMadeAt,
            String targetStationCode,
            int currentDelayMinutes,
            int predictedNextStationDelayMinutes,
            int predictedTotalDelayMinutes,
            Instant predictedEta,
            int historicalAdjustmentMinutes,
            HistoricalAdjustmentSource historicalAdjustmentSource,
            String historicalAdjustmentProvenance,
            double confidenceScore,
            PredictionEvaluationStatus evaluationStatus,
            Integer actualDelayMinutes,
            Integer errorMinutes,
            Instant evaluatedAt) {
        this(id, trainNumber, predictionMadeAt, targetStationCode, currentDelayMinutes,
                predictedNextStationDelayMinutes, predictedTotalDelayMinutes, predictedEta,
                historicalAdjustmentMinutes, historicalAdjustmentSource, historicalAdjustmentProvenance,
                confidenceScore, evaluationStatus, actualDelayMinutes, errorMinutes, evaluatedAt,
                PredictionEvaluationMode.LIVE_EVALUATION);
    }

    public PredictionSnapshot {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        predictionMadeAt = Guard.requireNonNull(predictionMadeAt, "predictionMadeAt");
        targetStationCode = Guard.requireNonBlank(targetStationCode, "targetStationCode");
        predictedEta = Guard.requireNonNull(predictedEta, "predictedEta");
        historicalAdjustmentSource = Guard.requireNonNull(historicalAdjustmentSource, "historicalAdjustmentSource");
        historicalAdjustmentProvenance = Guard.requireNonBlank(historicalAdjustmentProvenance, "historicalAdjustmentProvenance");
        evaluationStatus = Guard.requireNonNull(evaluationStatus, "evaluationStatus");
        evaluationMode = Guard.requireNonNull(evaluationMode, "evaluationMode");
        if (disruptionImpactMinutes != null) {
            Guard.requireNonNegative(disruptionImpactMinutes, "disruptionImpactMinutes");
        }
        nextStationHistoricalAdjustmentSource =
                Guard.requireNonNull(nextStationHistoricalAdjustmentSource, "nextStationHistoricalAdjustmentSource");
        nextStationHistoricalAdjustmentProvenance = Guard.requireNonBlank(
                nextStationHistoricalAdjustmentProvenance, "nextStationHistoricalAdjustmentProvenance");
        Guard.requireNonNegative(predictedNextStationDelayMinutes, "predictedNextStationDelayMinutes");
        Guard.requireNonNegative(predictedExtraDelayMinutes, "predictedExtraDelayMinutes");

        boolean evaluated = evaluationStatus == PredictionEvaluationStatus.EVALUATED_EXACT
                || evaluationStatus == PredictionEvaluationStatus.EVALUATED_APPROXIMATE;
        if (evaluated && (actualDelayMinutes == null || errorMinutes == null || evaluatedAt == null)) {
            throw new IllegalArgumentException(
                    "actualDelayMinutes, errorMinutes, and evaluatedAt must all be present when evaluationStatus is "
                            + evaluationStatus);
        }
        if (!evaluated && (actualDelayMinutes != null || errorMinutes != null || evaluatedAt != null)) {
            throw new IllegalArgumentException(
                    "actualDelayMinutes, errorMinutes, and evaluatedAt must all be null when evaluationStatus is "
                            + evaluationStatus);
        }
        if (quarantined) {
            quarantineReason = Guard.requireNonBlank(quarantineReason, "quarantineReason");
        } else if (quarantineReason != null) {
            throw new IllegalArgumentException("quarantineReason must be null when quarantined is false");
        }
    }
}
