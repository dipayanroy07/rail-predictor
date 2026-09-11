package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CalibrationAssessmentTest {

    @Test
    void blockedFactoryProducesInsufficientDataWithNoCandidate() {
        CalibrationAssessment assessment = CalibrationAssessment.blocked(
                "some.parameter", CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE, "not enough real data", 3);

        assertThat(assessment.status()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
        assertThat(assessment.selectedCandidateValue()).isNull();
        assertThat(assessment.trainingSampleCount()).isNull();
        assertThat(assessment.validationSampleCount()).isNull();
    }

    @Test
    void insufficientDataRequiresANonNoneBlockerReason() {
        assertThatThrownBy(() -> new CalibrationAssessment(
                "p", CalibrationStatus.INSUFFICIENT_DATA, CalibrationBlockerReason.NONE, "explanation", 3,
                null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatedStatusRequiresASelectedCandidateValue() {
        assertThatThrownBy(() -> new CalibrationAssessment(
                "p", CalibrationStatus.VALIDATED, CalibrationBlockerReason.NONE, "explanation", 100,
                70, 30, 0.3, null, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonCandidateStatusMustNotCarryACandidateValue() {
        assertThatThrownBy(() -> new CalibrationAssessment(
                "p", CalibrationStatus.INSUFFICIENT_DATA, CalibrationBlockerReason.INSUFFICIENT_SAMPLE_SIZE,
                "explanation", 3, null, null, null, 0.5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatedStatusWithACandidateValueIsAccepted() {
        CalibrationAssessment assessment = new CalibrationAssessment(
                "p", CalibrationStatus.VALIDATED, CalibrationBlockerReason.NONE, "explanation", 100,
                70, 30, 0.3, 0.45, 8.2);

        assertThat(assessment.selectedCandidateValue()).isEqualTo(0.45);
        assertThat(assessment.validationImprovementPercent()).isEqualTo(8.2);
    }
}
