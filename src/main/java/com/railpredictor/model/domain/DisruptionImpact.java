package com.railpredictor.model.domain;

/**
 * One real {@link RailwayDisruption}'s translated delay contribution (Phase 19) - the output of
 * {@code DisruptionImpactPolicy.evaluate(...)}. Deliberately separate from
 * {@link DisruptionResult} (a {@code DisruptionModel}'s *simulated* hypothesis): this represents a
 * policy's answer to "given this reported disruption, what delay should the prediction assume",
 * never a probabilistic guess.
 *
 * <p>Only ever carries {@link DisruptionImpactStatus#ESTIMATED} or
 * {@link DisruptionImpactStatus#PRESENT_BUT_NOT_ESTIMABLE} - the other two status values describe
 * "no disruption was even being evaluated" states that only make sense at the aggregate level (see
 * {@code DisruptionImpactAssessment}), never for one specific, actually-reported disruption.
 *
 * <p>{@code additionalDelayMinutes} is non-null and non-negative exactly when {@code status} is
 * {@code ESTIMATED}; {@code null} exactly when it is {@code PRESENT_BUT_NOT_ESTIMABLE} - never a
 * fabricated {@code 0} standing in for "couldn't compute this".
 */
public record DisruptionImpact(
        RailwayDisruptionType disruptionType,
        DisruptionImpactStatus status,
        Integer additionalDelayMinutes,
        String rationale,
        String sourceProvenance) {

    public DisruptionImpact {
        disruptionType = Guard.requireNonNull(disruptionType, "disruptionType");
        status = Guard.requireNonNull(status, "status");
        if (status != DisruptionImpactStatus.ESTIMATED && status != DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE) {
            throw new IllegalArgumentException(
                    "DisruptionImpact only ever represents ESTIMATED or PRESENT_BUT_NOT_ESTIMABLE, got " + status);
        }
        if (status == DisruptionImpactStatus.ESTIMATED) {
            if (additionalDelayMinutes == null) {
                throw new IllegalArgumentException("additionalDelayMinutes must be present when status is ESTIMATED");
            }
            Guard.requireNonNegative(additionalDelayMinutes, "additionalDelayMinutes");
        } else if (additionalDelayMinutes != null) {
            throw new IllegalArgumentException("additionalDelayMinutes must be null when status is PRESENT_BUT_NOT_ESTIMABLE");
        }
        rationale = Guard.requireNonBlank(rationale, "rationale");
        sourceProvenance = Guard.requireNonBlank(sourceProvenance, "sourceProvenance");
    }
}
