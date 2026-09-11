package com.railpredictor.model.domain;

/**
 * The result of attempting to empirically calibrate one numeric parameter against real evaluated
 * prediction outcomes (Phase 20) - e.g. {@code prediction.historical-adjustment.weight}, or a
 * {@code DisruptionImpactProperties} default. Always honest about *why* a confident recommendation
 * couldn't be made, rather than silently reporting a bare number.
 *
 * <p>{@code trainingSampleCount}/{@code validationSampleCount} are only populated for an assessment
 * that actually performed a real time-ordered train/validation split (see
 * {@code HistoricalWeightCalibrationAssessor}) - {@code null} otherwise.
 * {@code selectedCandidateValue}/{@code validationImprovementPercent} are only populated when
 * {@code status} is {@link CalibrationStatus#PROVISIONALLY_CALIBRATED} or
 * {@link CalibrationStatus#VALIDATED} - a real candidate was actually selected and validated.
 */
public record CalibrationAssessment(
        String parameterName,
        CalibrationStatus status,
        CalibrationBlockerReason blockerReason,
        String explanation,
        int realSampleCount,
        Integer trainingSampleCount,
        Integer validationSampleCount,
        Double currentValue,
        Double selectedCandidateValue,
        Double validationImprovementPercent) {

    public CalibrationAssessment {
        parameterName = Guard.requireNonBlank(parameterName, "parameterName");
        status = Guard.requireNonNull(status, "status");
        blockerReason = Guard.requireNonNull(blockerReason, "blockerReason");
        explanation = Guard.requireNonBlank(explanation, "explanation");
        Guard.requireNonNegative(realSampleCount, "realSampleCount");
        if (trainingSampleCount != null) {
            Guard.requireNonNegative(trainingSampleCount, "trainingSampleCount");
        }
        if (validationSampleCount != null) {
            Guard.requireNonNegative(validationSampleCount, "validationSampleCount");
        }
        boolean candidateSelected = status == CalibrationStatus.PROVISIONALLY_CALIBRATED || status == CalibrationStatus.VALIDATED;
        if (candidateSelected && selectedCandidateValue == null) {
            throw new IllegalArgumentException("selectedCandidateValue must be present when status is " + status);
        }
        if (!candidateSelected && (selectedCandidateValue != null || validationImprovementPercent != null)) {
            throw new IllegalArgumentException(
                    "selectedCandidateValue/validationImprovementPercent must be null when status is " + status);
        }
        if (status == CalibrationStatus.INSUFFICIENT_DATA && blockerReason == CalibrationBlockerReason.NONE) {
            throw new IllegalArgumentException("blockerReason must not be NONE when status is INSUFFICIENT_DATA");
        }
    }

    /** The common "not enough real data / structurally blocked" outcome - most assessments this
     * codebase can currently produce look like this. */
    public static CalibrationAssessment blocked(
            String parameterName, CalibrationBlockerReason reason, String explanation, int realSampleCount) {
        return new CalibrationAssessment(
                parameterName, CalibrationStatus.INSUFFICIENT_DATA, reason, explanation, realSampleCount,
                null, null, null, null, null);
    }
}
