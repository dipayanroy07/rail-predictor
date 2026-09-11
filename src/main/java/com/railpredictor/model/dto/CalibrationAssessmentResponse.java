package com.railpredictor.model.dto;

import com.railpredictor.model.domain.CalibrationBlockerReason;
import com.railpredictor.model.domain.CalibrationStatus;

/** JSON shape of {@code CalibrationAssessment} (Phase 20) - see that record's Javadoc for exactly
 * when each nullable field is populated. */
public record CalibrationAssessmentResponse(
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
}
