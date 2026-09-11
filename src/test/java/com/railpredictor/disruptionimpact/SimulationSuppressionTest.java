package com.railpredictor.disruptionimpact;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionImpact;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.domain.DisruptionImpactStatus;
import com.railpredictor.model.domain.RailwayDisruptionType;
import com.railpredictor.model.enums.DisruptionType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimulationSuppressionTest {

    private static DisruptionImpact impact(RailwayDisruptionType type, DisruptionImpactStatus status, Integer minutes) {
        return new DisruptionImpact(type, status, minutes, "r", DataProvenance.MOCK);
    }

    private static DisruptionImpactAssessment assessmentWith(DisruptionImpact... impacts) {
        return new DisruptionImpactAssessment(
                DisruptionImpactStatus.ESTIMATED, 10, List.of(impacts), false, CalibrationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void unavailableAssessmentSuppressesNothing() {
        Set<DisruptionType> suppressed = SimulationSuppression.suppressedSimulationTypes(DisruptionImpactAssessment.unavailable());

        assertThat(suppressed).isEmpty();
    }

    @Test
    void noKnownDisruptionSuppressesNothing() {
        DisruptionImpactAssessment assessment = new DisruptionImpactAssessment(
                DisruptionImpactStatus.NO_KNOWN_DISRUPTION, 0, List.of(), false, CalibrationStatus.INSUFFICIENT_DATA);

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).isEmpty();
    }

    @Test
    void aRealSpeedRestrictionSuppressesTheSimulatedSpeedRestrictionType() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, DisruptionImpactStatus.ESTIMATED, 10));

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).containsExactly(DisruptionType.SPEED_RESTRICTION);
    }

    @Test
    void aRealEngineeringBlockSuppressesTheSimulatedEngineeringBlockType() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.ENGINEERING_BLOCK, DisruptionImpactStatus.ESTIMATED, 15));

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).containsExactly(DisruptionType.ENGINEERING_BLOCK);
    }

    @Test
    void aRealSignalFailureSuppressesTheSimulatedSignalHaltType() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.SIGNAL_FAILURE, DisruptionImpactStatus.ESTIMATED, 10));

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).containsExactly(DisruptionType.SIGNAL_HALT);
    }

    @Test
    void aRealCongestionSuppressesTheSimulatedHighCongestionType() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, 10));

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).containsExactly(DisruptionType.HIGH_CONGESTION);
    }

    @Test
    void anUnestimableRealDisruptionStillSuppressesItsSimulatedCounterpart() {
        // Suppression must not depend on whether a delay figure could be computed - a confirmed
        // real signal failure means the simulated "what if" is no longer meaningful either way.
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.SIGNAL_FAILURE, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null));

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).containsExactly(DisruptionType.SIGNAL_HALT);
    }

    @Test
    void routeDiversionMaintenanceBlockAndOtherSuppressNothing() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.ROUTE_DIVERSION, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null),
                impact(RailwayDisruptionType.MAINTENANCE_BLOCK, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null),
                impact(RailwayDisruptionType.OTHER, DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null));

        assertThat(SimulationSuppression.suppressedSimulationTypes(assessment)).isEmpty();
    }

    @Test
    void heavyRainAndDenseFogAreNeverSuppressedSinceTheyHaveNoRailwayDisruptionCounterpart() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.ENGINEERING_BLOCK, DisruptionImpactStatus.ESTIMATED, 15),
                impact(RailwayDisruptionType.SIGNAL_FAILURE, DisruptionImpactStatus.ESTIMATED, 10),
                impact(RailwayDisruptionType.CONGESTION, DisruptionImpactStatus.ESTIMATED, 10),
                impact(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, DisruptionImpactStatus.ESTIMATED, 5));

        Set<DisruptionType> suppressed = SimulationSuppression.suppressedSimulationTypes(assessment);

        assertThat(suppressed).doesNotContain(DisruptionType.HEAVY_RAIN, DisruptionType.DENSE_FOG);
    }

    @Test
    void multipleDifferentRealDisruptionsSuppressAllTheirCounterparts() {
        DisruptionImpactAssessment assessment = assessmentWith(
                impact(RailwayDisruptionType.ENGINEERING_BLOCK, DisruptionImpactStatus.ESTIMATED, 15),
                impact(RailwayDisruptionType.SIGNAL_FAILURE, DisruptionImpactStatus.ESTIMATED, 10));

        Set<DisruptionType> suppressed = SimulationSuppression.suppressedSimulationTypes(assessment);

        assertThat(suppressed).containsExactlyInAnyOrder(DisruptionType.ENGINEERING_BLOCK, DisruptionType.SIGNAL_HALT);
    }
}
