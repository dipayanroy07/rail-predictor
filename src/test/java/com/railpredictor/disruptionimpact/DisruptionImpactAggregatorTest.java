package com.railpredictor.disruptionimpact;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.DisruptionImpactProperties;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.domain.DisruptionImpactStatus;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionAvailability;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RailwayDisruptionType;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.TrainStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisruptionImpactAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Station FROM = new Station("KOTA", "Kota Jn");
    private static final Station TO = new Station("RTM", "Ratlam Jn");
    private static final RouteSection SECTION = new RouteSection(FROM, TO, 100.0);
    private static final LiveTrainData TRAIN =
            new LiveTrainData("12952", "Rajdhani Express", TrainStatus.RUNNING, 5, FROM, TO, 100.0, 465.0, 100.0);

    private final DisruptionImpactProperties properties = new DisruptionImpactProperties(15, 10, 10, 15, 25);
    private final DisruptionImpactAggregator aggregator =
            new DisruptionImpactAggregator(new HeuristicDisruptionImpactPolicy(properties), properties);

    private static RailwayDisruption disruption(
            RailwayDisruptionType type, Instant effectiveFrom, Instant effectiveUntil, Instant observedAt) {
        return new RailwayDisruption(
                type, "12952", "KOTA", "RTM", null, null, effectiveFrom, effectiveUntil, observedAt,
                DataProvenance.MOCK, null);
    }

    private static RailwayDisruptionQueryResult queryResult(List<RailwayDisruption> disruptions) {
        return new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.AVAILABLE, disruptions);
    }

    private static RailwayDisruptionQueryResult unavailableQueryResult() {
        return new RailwayDisruptionQueryResult(
                "12952", "KOTA", "RTM", RailwayDisruptionAvailability.UNAVAILABLE, List.of());
    }

    @Test
    void unavailableAvailabilityProducesDataUnavailable() {
        DisruptionImpactAssessment assessment = aggregator.assess(unavailableQueryResult(), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.DATA_UNAVAILABLE);
        assertThat(assessment.additionalDelayMinutes()).isNull();
        assertThat(assessment.calibrationStatus()).isEqualTo(CalibrationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void emptyDisruptionListProducesNoKnownDisruption() {
        DisruptionImpactAssessment assessment = aggregator.assess(queryResult(List.of()), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.NO_KNOWN_DISRUPTION);
        assertThat(assessment.additionalDelayMinutes()).isZero();
    }

    @Test
    void anActiveDisruptionIsEstimated() {
        RailwayDisruption active = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));

        DisruptionImpactAssessment assessment = aggregator.assess(queryResult(List.of(active)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(assessment.additionalDelayMinutes()).isEqualTo(properties.engineeringBlockDefaultDelayMinutes());
        assertThat(assessment.contributingImpacts()).hasSize(1);
    }

    @Test
    void aFutureEffectiveDisruptionIsTreatedAsNoKnownDisruption() {
        RailwayDisruption future = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.plusSeconds(3600), null, NOW.minusSeconds(7200));

        DisruptionImpactAssessment assessment = aggregator.assess(queryResult(List.of(future)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.NO_KNOWN_DISRUPTION);
        assertThat(assessment.additionalDelayMinutes()).isZero();
        assertThat(assessment.contributingImpacts()).isEmpty();
    }

    @Test
    void anExpiredDisruptionIsTreatedAsNoKnownDisruption() {
        RailwayDisruption expired = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(7200), NOW.minusSeconds(3600), NOW.minusSeconds(7200));

        DisruptionImpactAssessment assessment = aggregator.assess(queryResult(List.of(expired)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.NO_KNOWN_DISRUPTION);
    }

    @Test
    void anUndatedDisruptionWithUnknownValidityIsTreatedAsNoKnownDisruption() {
        RailwayDisruption undated = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, null, null, NOW.minusSeconds(3600));

        DisruptionImpactAssessment assessment = aggregator.assess(queryResult(List.of(undated)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.NO_KNOWN_DISRUPTION);
    }

    @Test
    void anActiveButUnestimableDisruptionProducesPresentButNotEstimable() {
        RailwayDisruption unrouted = disruption(
                RailwayDisruptionType.ROUTE_DIVERSION, NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));

        DisruptionImpactAssessment assessment = aggregator.assess(queryResult(List.of(unrouted)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE);
        assertThat(assessment.additionalDelayMinutes()).isNull();
        assertThat(assessment.contributingImpacts()).hasSize(1);
    }

    @Test
    void duplicateDisruptionsWithTheSameIdentityAreCountedOnce() {
        RailwayDisruption first = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(7200), null, NOW.minusSeconds(7200));
        RailwayDisruption reReported = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(7200), null, NOW.minusSeconds(1800));

        DisruptionImpactAssessment assessment =
                aggregator.assess(queryResult(List.of(first, reReported)), SECTION, TRAIN, NOW);

        assertThat(assessment.contributingImpacts()).hasSize(1);
        assertThat(assessment.additionalDelayMinutes()).isEqualTo(properties.engineeringBlockDefaultDelayMinutes());
    }

    @Test
    void differentTypesOnTheSameSectionAreSummed() {
        RailwayDisruption engineeringBlock = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));
        RailwayDisruption signalFailure = disruption(
                RailwayDisruptionType.SIGNAL_FAILURE, NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));

        DisruptionImpactAssessment assessment =
                aggregator.assess(queryResult(List.of(engineeringBlock, signalFailure)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(assessment.additionalDelayMinutes()).isEqualTo(
                properties.engineeringBlockDefaultDelayMinutes() + properties.signalFailureDefaultDelayMinutes());
        assertThat(assessment.contributingImpacts()).hasSize(2);
        assertThat(assessment.cappedByMaximumAggregate()).isFalse();
    }

    @Test
    void theAggregateIsCappedAtTheConfiguredMaximum() {
        // engineeringBlock(15) + signalFailure(10) + congestion(10) = 35, cap is 25.
        RailwayDisruption engineeringBlock = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));
        RailwayDisruption signalFailure = disruption(
                RailwayDisruptionType.SIGNAL_FAILURE, NOW.minusSeconds(3600), null, NOW.minusSeconds(3500));
        RailwayDisruption congestion = disruption(
                RailwayDisruptionType.CONGESTION, NOW.minusSeconds(3600), null, NOW.minusSeconds(3400));

        DisruptionImpactAssessment assessment = aggregator.assess(
                queryResult(List.of(engineeringBlock, signalFailure, congestion)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(assessment.additionalDelayMinutes()).isEqualTo(properties.maxAggregateDelayMinutes());
        assertThat(assessment.cappedByMaximumAggregate()).isTrue();
    }

    @Test
    void mixedEstimableAndNotEstimableDisruptionsStillProduceAnEstimatedTotalFromTheEstimableOnes() {
        RailwayDisruption engineeringBlock = disruption(
                RailwayDisruptionType.ENGINEERING_BLOCK, NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));
        RailwayDisruption diversion = disruption(
                RailwayDisruptionType.ROUTE_DIVERSION, NOW.minusSeconds(3600), null, NOW.minusSeconds(3500));

        DisruptionImpactAssessment assessment =
                aggregator.assess(queryResult(List.of(engineeringBlock, diversion)), SECTION, TRAIN, NOW);

        assertThat(assessment.status()).isEqualTo(DisruptionImpactStatus.ESTIMATED);
        assertThat(assessment.additionalDelayMinutes()).isEqualTo(properties.engineeringBlockDefaultDelayMinutes());
        assertThat(assessment.contributingImpacts()).hasSize(2);
    }
}
