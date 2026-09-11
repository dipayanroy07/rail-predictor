package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.model.domain.RemainingRoute;
import com.railpredictor.model.domain.RemainingRouteHistoricalStatus;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import com.railpredictor.model.domain.RemainingRouteSection;
import com.railpredictor.model.domain.RouteCompleteness;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import com.railpredictor.model.domain.Station;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemainingRouteHistoricalAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    private final HistoricalSectionDelayProvider provider = mock(HistoricalSectionDelayProvider.class);
    private final RemainingRouteHistoricalAggregator aggregator = new RemainingRouteHistoricalAggregator(provider);

    private static Station station(String code) {
        return new Station(code, code + " Station");
    }

    private static RouteSection section(String from, String to) {
        return new RouteSection(station(from), station(to), null);
    }

    private static RemainingRoute route(RouteCompleteness completeness, String... stationCodes) {
        List<RemainingRouteSection> sections = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < stationCodes.length; i++) {
            sections.add(new RemainingRouteSection(section(stationCodes[i], stationCodes[i + 1]), i + 1, i + 2));
        }
        Station destination = station(stationCodes[stationCodes.length - 1]);
        return new RemainingRoute(station(stationCodes[0]), destination, sections, completeness);
    }

    private static SectionHistoricalDelayResult available(String from, String to, double avgDelayChange, String source) {
        HistoricalSectionDelayProfile profile = new HistoricalSectionDelayProfile(
                "12952", from, to, 15, avgDelayChange, avgDelayChange, 1.0, source, Instant.parse("2026-09-10T08:00:00Z"));
        return new SectionHistoricalDelayResult("12952", from, to, SectionHistoricalDelayStatus.AVAILABLE, profile);
    }

    private static SectionHistoricalDelayResult insufficientSamples(String from, String to) {
        HistoricalSectionDelayProfile profile = new HistoricalSectionDelayProfile(
                "12952", from, to, 2, 3.0, 3.0, 0.5, DataProvenance.RAILRADAR, Instant.parse("2026-09-10T08:00:00Z"));
        return new SectionHistoricalDelayResult("12952", from, to, SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES, profile);
    }

    private static SectionHistoricalDelayResult notFound(String from, String to) {
        return new SectionHistoricalDelayResult("12952", from, to, SectionHistoricalDelayStatus.NOT_FOUND, null);
    }

    @Test
    void noRemainingSectionsProducesAnExplicitNoRemainingSectionsStatus() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "D"); // already at destination -> zero sections

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.status()).isEqualTo(RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS);
        assertThat(summary.availableSectionCount()).isZero();
        assertThat(summary.totalDelayChangeMinutes()).isNull();
        assertThat(summary.provenance()).isEqualTo(DataProvenance.UNAVAILABLE);
        assertThat(summary.sectionResults()).isEmpty();
    }

    @Test
    void allSectionsAvailableSumsThePositiveDelayChangesAndReportsAllAvailable() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B", "C", "D");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 4.0, DataProvenance.RAILRADAR));
        when(provider.getSectionDelay("12952", section("B", "C"), NOW)).thenReturn(available("B", "C", 2.0, DataProvenance.RAILRADAR));
        when(provider.getSectionDelay("12952", section("C", "D"), NOW)).thenReturn(available("C", "D", -1.0, DataProvenance.RAILRADAR));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.status()).isEqualTo(RemainingRouteHistoricalStatus.ALL_SECTIONS_AVAILABLE);
        assertThat(summary.availableSectionCount()).isEqualTo(3);
        assertThat(summary.totalDelayChangeMinutes()).isEqualTo(5.0); // 4 + 2 - 1, signed, never clamped
        assertThat(summary.sectionResults()).hasSize(3);
    }

    @Test
    void zeroDelayChangeSectionsSumToZero() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 0.0, DataProvenance.RAILRADAR));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.totalDelayChangeMinutes()).isEqualTo(0.0);
    }

    @Test
    void negativeRecoveryDelayChangesArePreservedNotClamped() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", -7.0, DataProvenance.RAILRADAR));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.totalDelayChangeMinutes()).isEqualTo(-7.0);
    }

    @Test
    void mixedPositiveAndNegativeSectionsSumSignedCorrectly() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B", "C");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 10.0, DataProvenance.RAILRADAR));
        when(provider.getSectionDelay("12952", section("B", "C"), NOW)).thenReturn(available("B", "C", -3.0, DataProvenance.RAILRADAR));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.totalDelayChangeMinutes()).isEqualTo(7.0);
    }

    @Test
    void someSectionsAvailableIsPartial() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B", "C");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 4.0, DataProvenance.RAILRADAR));
        when(provider.getSectionDelay("12952", section("B", "C"), NOW)).thenReturn(notFound("B", "C"));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.status()).isEqualTo(RemainingRouteHistoricalStatus.PARTIAL_SECTIONS_AVAILABLE);
        assertThat(summary.availableSectionCount()).isEqualTo(1);
        assertThat(summary.totalDelayChangeMinutes()).isEqualTo(4.0);
        assertThat(summary.sectionResults()).hasSize(2);
    }

    @Test
    void noUsableHistoricalDataAcrossAllSectionsIsNoSectionsAvailable() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(notFound("A", "B"));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.status()).isEqualTo(RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE);
        assertThat(summary.totalDelayChangeMinutes()).isNull();
        assertThat(summary.provenance()).isEqualTo(DataProvenance.UNAVAILABLE);
    }

    @Test
    void insufficientSamplesDoesNotContributeToTheTotalButIsPreserved() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(insufficientSamples("A", "B"));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.status()).isEqualTo(RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE);
        assertThat(summary.sectionResults().get(0).status()).isEqualTo(SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES);
    }

    @Test
    void notFoundDoesNotContributeToTheTotalButIsPreserved() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(notFound("A", "B"));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.sectionResults().get(0).status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
        assertThat(summary.sectionResults().get(0).profile()).isNull();
    }

    @Test
    void provenanceIsPreservedAcrossMixedSourcesAmongContributingSections() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B", "C");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 4.0, DataProvenance.RAILRADAR));
        when(provider.getSectionDelay("12952", section("B", "C"), NOW)).thenReturn(available("B", "C", 2.0, DataProvenance.MOCK));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.provenance()).contains("mixed(");
        assertThat(summary.provenance()).contains(DataProvenance.MOCK).contains(DataProvenance.RAILRADAR);
    }

    @Test
    void mockOnlyProvenanceIsPreservedNotUpgradedToReal() {
        RemainingRoute route = route(RouteCompleteness.COMPLETE, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 4.0, DataProvenance.MOCK));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.provenance()).isEqualTo(DataProvenance.MOCK);
    }

    @Test
    void routeCompletenessIsCarriedThroughUnchanged() {
        RemainingRoute route = route(RouteCompleteness.PARTIAL, "A", "B");
        when(provider.getSectionDelay("12952", section("A", "B"), NOW)).thenReturn(available("A", "B", 1.0, DataProvenance.RAILRADAR));

        RemainingRouteHistoricalSummary summary = aggregator.summarize("12952", route, NOW);

        assertThat(summary.routeCompleteness()).isEqualTo(RouteCompleteness.PARTIAL);
    }
}
