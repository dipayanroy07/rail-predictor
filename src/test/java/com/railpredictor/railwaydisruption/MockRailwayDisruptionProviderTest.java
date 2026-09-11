package com.railpredictor.railwaydisruption;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.RailwayDisruptionMockProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionAvailability;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RailwayDisruptionType;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MockRailwayDisruptionProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
    private static final RouteSection SECTION =
            new RouteSection(new Station("KOTA", "Kota Jn"), new Station("RTM", "Ratlam Jn"), 465.0);

    @Test
    void reportsAvailableWithNoDisruptionsWhenNothingIsConfigured() {
        RailwayDisruptionMockProperties properties =
                new RailwayDisruptionMockProperties(false, null, null, null, null, null, null);
        MockRailwayDisruptionProvider provider = new MockRailwayDisruptionProvider(properties, FIXED_CLOCK);

        RailwayDisruptionQueryResult result = provider.getDisruptions("12952", SECTION, Instant.now(FIXED_CLOCK));

        assertThat(result.availability()).isEqualTo(RailwayDisruptionAvailability.AVAILABLE);
        assertThat(result.disruptions()).isEmpty();
    }

    @Test
    void returnsTheConfiguredDisruptionWhenTrainAndSectionMatch() {
        RailwayDisruptionMockProperties properties = new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, "12952", "KOTA", "RTM", 40.0, "moderate");
        MockRailwayDisruptionProvider provider = new MockRailwayDisruptionProvider(properties, FIXED_CLOCK);

        RailwayDisruptionQueryResult result = provider.getDisruptions("12952", SECTION, Instant.now(FIXED_CLOCK));

        assertThat(result.availability()).isEqualTo(RailwayDisruptionAvailability.AVAILABLE);
        assertThat(result.disruptions()).hasSize(1);
        RailwayDisruption disruption = result.disruptions().get(0);
        assertThat(disruption.type()).isEqualTo(RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION);
        assertThat(disruption.restrictedSpeedKmh()).isEqualTo(40.0);
        assertThat(disruption.source()).isEqualTo(DataProvenance.MOCK);
    }

    @Test
    void aRouteWideDisruptionWithNoConfiguredTrainNumberMatchesAnyTrain() {
        RailwayDisruptionMockProperties properties = new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.ENGINEERING_BLOCK, null, "KOTA", "RTM", null, null);
        MockRailwayDisruptionProvider provider = new MockRailwayDisruptionProvider(properties, FIXED_CLOCK);

        RailwayDisruptionQueryResult resultForOneTrain = provider.getDisruptions("12952", SECTION, Instant.now(FIXED_CLOCK));
        RailwayDisruptionQueryResult resultForAnotherTrain = provider.getDisruptions("99999", SECTION, Instant.now(FIXED_CLOCK));

        assertThat(resultForOneTrain.disruptions()).hasSize(1);
        assertThat(resultForAnotherTrain.disruptions()).hasSize(1);
        assertThat(resultForOneTrain.disruptions().get(0).trainNumber()).isNull();
    }

    @Test
    void aTrainSpecificDisruptionNeverMatchesADifferentTrain() {
        RailwayDisruptionMockProperties properties = new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.SIGNAL_FAILURE, "12952", "KOTA", "RTM", null, null);
        MockRailwayDisruptionProvider provider = new MockRailwayDisruptionProvider(properties, FIXED_CLOCK);

        RailwayDisruptionQueryResult result = provider.getDisruptions("99999", SECTION, Instant.now(FIXED_CLOCK));

        assertThat(result.availability()).isEqualTo(RailwayDisruptionAvailability.AVAILABLE);
        assertThat(result.disruptions()).isEmpty();
    }

    @Test
    void neverMatchesADifferentSection() {
        RailwayDisruptionMockProperties properties = new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.SIGNAL_FAILURE, "12952", "KOTA", "RTM", null, null);
        MockRailwayDisruptionProvider provider = new MockRailwayDisruptionProvider(properties, FIXED_CLOCK);
        RouteSection differentSection =
                new RouteSection(new Station("NDLS", "New Delhi"), new Station("AGC", "Agra Cantt"), 200.0);

        RailwayDisruptionQueryResult result = provider.getDisruptions("12952", differentSection, Instant.now(FIXED_CLOCK));

        assertThat(result.disruptions()).isEmpty();
    }

    @Test
    void theConfiguredDisruptionIsReportedActiveAsOfNow() {
        RailwayDisruptionMockProperties properties = new RailwayDisruptionMockProperties(
                true, RailwayDisruptionType.CONGESTION, null, "KOTA", "RTM", null, null);
        MockRailwayDisruptionProvider provider = new MockRailwayDisruptionProvider(properties, FIXED_CLOCK);

        RailwayDisruptionQueryResult result = provider.getDisruptions("12952", SECTION, Instant.now(FIXED_CLOCK));

        RailwayDisruption disruption = result.disruptions().get(0);
        assertThat(RailwayDisruptionStatusClassifier.classify(disruption, Instant.now(FIXED_CLOCK)))
                .isEqualTo(com.railpredictor.model.domain.RailwayDisruptionStatus.ACTIVE);
        assertThat(disruption.observedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
    }
}
