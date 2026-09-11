package com.railpredictor.model.domain;

import java.util.List;

/**
 * The rolled-up result of translating every currently-active real disruption on one section into
 * a single predicted delay contribution (Phase 19) - produced by
 * {@code disruptionimpact.DisruptionImpactAggregator}, consumed by {@code PredictionEngine}
 * exactly like {@code HistoricalAdjustmentResolution} is: a transparent record of what was decided
 * and why, not just a bare number.
 *
 * <p>{@code additionalDelayMinutes} is non-null (and {@code 0} for
 * {@link DisruptionImpactStatus#NO_KNOWN_DISRUPTION}) exactly when {@code status} is
 * {@code NO_KNOWN_DISRUPTION} or {@code ESTIMATED}; {@code null} exactly when it is
 * {@code DATA_UNAVAILABLE} or {@code PRESENT_BUT_NOT_ESTIMABLE} - see
 * {@link DisruptionImpactStatus}'s own Javadoc for why this distinction is mandatory.
 *
 * <p>{@code contributingImpacts} lists every currently-active, de-duplicated disruption's own
 * {@link DisruptionImpact} that was considered (both {@code ESTIMATED} and
 * {@code PRESENT_BUT_NOT_ESTIMABLE} ones) - empty for {@code NO_KNOWN_DISRUPTION}/
 * {@code DATA_UNAVAILABLE}. This is also how {@code disruptionimpact.SimulationSuppression}
 * determines which simulated {@code DisruptionModel}s to suppress for this run - see its own
 * Javadoc for why a real, confirmed disruption always suppresses its simulated counterpart
 * regardless of whether a delay figure could be estimated for it.
 *
 * <p>{@code cappedByMaximumAggregate} is {@code true} when the raw sum of estimated impacts
 * exceeded {@code railway-disruption-impact.max-aggregate-delay-minutes} and was clamped -
 * surfaced so a cap is never silently invisible.
 */
public record DisruptionImpactAssessment(
        DisruptionImpactStatus status,
        Integer additionalDelayMinutes,
        List<DisruptionImpact> contributingImpacts,
        boolean cappedByMaximumAggregate,
        CalibrationStatus calibrationStatus) {

    /** The explicit "no real disruption data was even consulted" default - used wherever a caller
     * (a legacy {@code PredictionResult}/{@code PredictionEngine} overload, or a fetch failure)
     * has no real assessment to report. Deliberately identical in shape to a genuine
     * {@link DisruptionImpactStatus#DATA_UNAVAILABLE} result - "not queried" and "queried but
     * failed" both honestly mean "we don't know", and must never be presented as anything more
     * confident than that. */
    public static DisruptionImpactAssessment unavailable() {
        return new DisruptionImpactAssessment(
                DisruptionImpactStatus.DATA_UNAVAILABLE, null, List.of(), false, CalibrationStatus.INSUFFICIENT_DATA);
    }

    public DisruptionImpactAssessment {
        status = Guard.requireNonNull(status, "status");
        contributingImpacts = List.copyOf(Guard.requireNonNull(contributingImpacts, "contributingImpacts"));
        calibrationStatus = Guard.requireNonNull(calibrationStatus, "calibrationStatus");

        boolean minutesExpected = status == DisruptionImpactStatus.NO_KNOWN_DISRUPTION
                || status == DisruptionImpactStatus.ESTIMATED;
        if (minutesExpected && additionalDelayMinutes == null) {
            throw new IllegalArgumentException("additionalDelayMinutes must be present when status is " + status);
        }
        if (!minutesExpected && additionalDelayMinutes != null) {
            throw new IllegalArgumentException("additionalDelayMinutes must be null when status is " + status);
        }
        if (additionalDelayMinutes != null) {
            Guard.requireNonNegative(additionalDelayMinutes, "additionalDelayMinutes");
        }
        if ((status == DisruptionImpactStatus.NO_KNOWN_DISRUPTION || status == DisruptionImpactStatus.DATA_UNAVAILABLE)
                && !contributingImpacts.isEmpty()) {
            throw new IllegalArgumentException("contributingImpacts must be empty when status is " + status);
        }
    }
}
