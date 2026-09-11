package com.railpredictor.disruptionimpact;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.DisruptionImpactProperties;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionImpact;
import com.railpredictor.model.domain.DisruptionImpactStatus;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionType;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.TrainStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HeuristicDisruptionImpactPolicyTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-10T10:00:00Z");
    private static final Station FROM = new Station("KOTA", "Kota Jn");
    private static final Station TO = new Station("RTM", "Ratlam Jn");

    private final DisruptionImpactProperties properties = new DisruptionImpactProperties(15, 10, 10, 60, 90);
    private final HeuristicDisruptionImpactPolicy policy = new HeuristicDisruptionImpactPolicy(properties);

    private static RailwayDisruption disruption(RailwayDisruptionType type, Double restrictedSpeedKmh) {
        return new RailwayDisruption(
                type, "12952", "KOTA", "RTM", restrictedSpeedKmh, null, OBSERVED_AT, null, OBSERVED_AT,
                DataProvenance.MOCK, null);
    }

    private static LiveTrainData trainWithSpeed(Double speedKmh) {
        return new LiveTrainData("12952", "Rajdhani Express", TrainStatus.RUNNING, 5,
                FROM, TO, 100.0, 465.0, speedKmh);
    }

    private static RouteSection section(Double distanceKm) {
        return new RouteSection(FROM, TO, distanceKm);
    }

    @Test
    void calibrationStatusIsAlwaysInsufficientData() {
        assertThat(policy.calibrationStatus()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
    }

    // --- TEMPORARY_SPEED_RESTRICTION ---

    @Test
    void speedRestrictionComputesThePhysicallyGroundedDelay() {
        // distance 100km: at 100km/h -> 60min, at 50km/h -> 120min -> delay 60min, capped at 60.
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 50.0);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(impact.additionalDelayMinutes()).isEqualTo(60);
    }

    @Test
    void speedRestrictionUncappedComputesExactMinutes() {
        // distance 50km: at 80km/h -> 37.5min, at 40km/h -> 75min -> delay 37.5 ~ rounds to 38.
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 40.0);

        DisruptionImpact impact = policy.evaluate(d, section(50.0), trainWithSpeed(80.0));

        assertThat(impact.additionalDelayMinutes()).isEqualTo(38);
    }

    @Test
    void speedRestrictionWithMissingRestrictedSpeedIsNotEstimable() {
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
        assertThat(impact.additionalDelayMinutes()).isNull();
    }

    @Test
    void speedRestrictionWithMissingCurrentSpeedIsNotEstimable() {
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 50.0);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(null));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void speedRestrictionWithMissingSectionDistanceIsNotEstimable() {
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 50.0);

        DisruptionImpact impact = policy.evaluate(d, section(null), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void speedRestrictionWithZeroDistanceIsNotEstimable() {
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 50.0);

        DisruptionImpact impact = policy.evaluate(d, section(0.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void speedRestrictionWithZeroOrNegativeRestrictedSpeedIsNotEstimable() {
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 0.0);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void speedRestrictionNotActuallySlowerThanCurrentSpeedIsNotEstimable() {
        // restricted speed >= current speed - not actually restrictive, never fabricate a negative delay.
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 120.0);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void speedRestrictionEqualToCurrentSpeedIsNotEstimable() {
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 100.0);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void speedRestrictionResultIsCappedAtTheConfiguredMaximum() {
        // distance 1000km: at 100 -> 600min, at 10 -> 6000min -> delay 5400min, capped at 60.
        RailwayDisruption d = disruption(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, 10.0);

        DisruptionImpact impact = policy.evaluate(d, section(1000.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(impact.additionalDelayMinutes()).isEqualTo(properties.maxSingleDisruptionDelayMinutes());
    }

    // --- ENGINEERING_BLOCK / SIGNAL_FAILURE / CONGESTION: flat heuristics ---

    @Test
    void engineeringBlockUsesTheConfiguredFlatDefault() {
        RailwayDisruption d = disruption(RailwayDisruptionType.ENGINEERING_BLOCK, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(impact.additionalDelayMinutes()).isEqualTo(properties.engineeringBlockDefaultDelayMinutes());
    }

    @Test
    void signalFailureUsesTheConfiguredFlatDefault() {
        RailwayDisruption d = disruption(RailwayDisruptionType.SIGNAL_FAILURE, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(impact.additionalDelayMinutes()).isEqualTo(properties.signalFailureDefaultDelayMinutes());
    }

    @Test
    void congestionUsesTheConfiguredFlatDefault() {
        RailwayDisruption d = disruption(RailwayDisruptionType.CONGESTION, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(impact.additionalDelayMinutes()).isEqualTo(properties.congestionDefaultDelayMinutes());
    }

    @Test
    void congestionIgnoresSeverityTextEvenWhenSupplied() {
        RailwayDisruption withSeverity = new RailwayDisruption(
                RailwayDisruptionType.CONGESTION, "12952", "KOTA", "RTM", null, "severe", OBSERVED_AT, null,
                OBSERVED_AT, DataProvenance.MOCK, null);

        DisruptionImpact impact = policy.evaluate(withSeverity, section(100.0), trainWithSpeed(100.0));

        // Deliberately unaffected by severity text - see this class's own Javadoc.
        assertThat(impact.additionalDelayMinutes()).isEqualTo(properties.congestionDefaultDelayMinutes());
    }

    // --- ROUTE_DIVERSION / MAINTENANCE_BLOCK / OTHER: no rule ---

    @Test
    void routeDiversionHasNoImpactRuleYet() {
        RailwayDisruption d = disruption(RailwayDisruptionType.ROUTE_DIVERSION, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void maintenanceBlockHasNoImpactRuleYet() {
        RailwayDisruption d = disruption(RailwayDisruptionType.MAINTENANCE_BLOCK, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }

    @Test
    void otherHasNoImpactRuleYet() {
        RailwayDisruption d = disruption(RailwayDisruptionType.OTHER, null);

        DisruptionImpact impact = policy.evaluate(d, section(100.0), trainWithSpeed(100.0));

        assertThat(impact.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
    }
}
