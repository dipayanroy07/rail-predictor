package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.model.domain.HistoricalObservation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalDelayProfileAggregatorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);

    private final HistoricalDelayProfileAggregator aggregator = new HistoricalDelayProfileAggregator(FIXED_CLOCK);

    private static HistoricalObservation observation(LocalDate journeyDate, Integer arrivalDelayMinutes, String source) {
        return new HistoricalObservation(
                "12952", journeyDate, "KOTA", 5, "20:39", "20:39", "20:41", "20:41",
                arrivalDelayMinutes, arrivalDelayMinutes, Instant.parse("2026-09-10T08:00:00Z"), source);
    }

    @Test
    void zeroObservationsProduceAnEmptyProfileNotAFabricatedOne() {
        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", List.of());

        assertThat(profile.sampleCount()).isZero();
        assertThat(profile.averageArrivalDelayMinutes()).isZero();
        assertThat(profile.medianArrivalDelayMinutes()).isZero();
        assertThat(profile.standardDeviationMinutes()).isZero();
        assertThat(profile.source()).isEqualTo(DataProvenance.UNAVAILABLE);
    }

    @Test
    void allObservationsLackingADelayFigureAlsoProduceAnEmptyProfile() {
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), null, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), null, DataProvenance.RAILRADAR));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.sampleCount()).isZero();
    }

    @Test
    void oneObservationGivesZeroStandardDeviationNotUndefined() {
        HistoricalDelayProfile profile = aggregator.aggregate(
                "12952", "KOTA", List.of(observation(LocalDate.of(2026, 9, 1), 12, DataProvenance.RAILRADAR)));

        assertThat(profile.sampleCount()).isEqualTo(1);
        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(12.0);
        assertThat(profile.medianArrivalDelayMinutes()).isEqualTo(12.0);
        assertThat(profile.standardDeviationMinutes()).isZero();
    }

    @Test
    void oddCountUsesTheExactMiddleValueForTheMedian() {
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), 30, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 3), 20, DataProvenance.RAILRADAR));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.sampleCount()).isEqualTo(3);
        assertThat(profile.medianArrivalDelayMinutes()).isEqualTo(20.0);
        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(20.0);
    }

    @Test
    void evenCountAveragesTheTwoMiddleValuesForTheMedian() {
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), 20, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 3), 30, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 4), 40, DataProvenance.RAILRADAR));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.sampleCount()).isEqualTo(4);
        // sorted: 10,20,30,40 -> middle two are 20 and 30 -> median 25.0
        assertThat(profile.medianArrivalDelayMinutes()).isEqualTo(25.0);
    }

    @Test
    void computesSampleStandardDeviationWithBesselsCorrection() {
        // values 10,20,30 -> mean 20 -> squared diffs 100,0,100 -> sum 200 -> /(3-1)=100 -> sqrt=10
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), 20, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 3), 30, DataProvenance.RAILRADAR));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.standardDeviationMinutes()).isCloseTo(10.0, within(0.0001));
    }

    @Test
    void negativeDelaysAreIncludedUnclampedInTheStatistics() {
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), -4, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), -2, DataProvenance.RAILRADAR));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(-3.0);
        assertThat(profile.medianArrivalDelayMinutes()).isEqualTo(-3.0);
    }

    @Test
    void observationsWithANullDelayAreExcludedFromSampleCountAndStatistics() {
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), null, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 3), 20, DataProvenance.RAILRADAR));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.sampleCount()).isEqualTo(2);
        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(15.0);
    }

    @Test
    void singleSourceProfilesReportThatSourceUnchanged() {
        HistoricalDelayProfile profile = aggregator.aggregate(
                "12952", "KOTA", List.of(observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR)));

        assertThat(profile.source()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void mixedSourceObservationsProduceAnExplicitlyMixedProfileSourceNeverASingleClaimedSource() {
        List<HistoricalObservation> observations = List.of(
                observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR),
                observation(LocalDate.of(2026, 9, 2), 12, DataProvenance.MOCK));

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", observations);

        assertThat(profile.source()).isNotEqualTo(DataProvenance.RAILRADAR);
        assertThat(profile.source()).contains(DataProvenance.RAILRADAR).contains(DataProvenance.MOCK);
    }

    @Test
    void stampsComputedAtFromTheInjectedClock() {
        HistoricalDelayProfile profile = aggregator.aggregate(
                "12952", "KOTA", List.of(observation(LocalDate.of(2026, 9, 1), 10, DataProvenance.RAILRADAR)));

        assertThat(profile.computedAt()).isEqualTo(Instant.parse("2026-09-10T09:00:00Z"));
    }

    // --- Temporal cutoff (Phase 16H-7) ---

    private static HistoricalObservation observationAt(Instant observedAt, Integer arrivalDelayMinutes) {
        return new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5, "20:39", "20:39", "20:41", "20:41",
                arrivalDelayMinutes, arrivalDelayMinutes, observedAt, DataProvenance.RAILRADAR);
    }

    @Test
    void anObservationBeforeTheCutoffIsIncluded() {
        Instant cutoff = Instant.parse("2026-09-09T12:00:00Z");
        HistoricalObservation observation = observationAt(Instant.parse("2026-09-09T11:00:00Z"), 10);

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", List.of(observation), cutoff);

        assertThat(profile.sampleCount()).isEqualTo(1);
        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(10.0);
    }

    @Test
    void anObservationExactlyAtTheCutoffIsIncluded() {
        // Chosen semantics: the cutoff instant itself is inclusive - identical convention to
        // HistoricalSectionDelayProfileAggregator.
        Instant cutoff = Instant.parse("2026-09-09T11:05:00Z");
        HistoricalObservation observation = observationAt(cutoff, 10);

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", List.of(observation), cutoff);

        assertThat(profile.sampleCount()).isEqualTo(1);
    }

    @Test
    void anObservationAfterTheCutoffIsExcludedEntirely() {
        Instant cutoff = Instant.parse("2026-09-09T11:00:00Z");
        HistoricalObservation before = observationAt(Instant.parse("2026-09-09T10:00:00Z"), 10);
        HistoricalObservation after = observationAt(Instant.parse("2026-09-09T11:05:00Z"), 999);

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", List.of(before, after), cutoff);

        // The future observation must never influence the profile - not its sample count, and
        // not its statistics (999 would wildly skew the average if it leaked in).
        assertThat(profile.sampleCount()).isEqualTo(1);
        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(10.0);
    }

    @Test
    void emptyHistoricalDatasetWithACutoffStillProducesAnEmptyProfileNotAFabricatedOne() {
        HistoricalDelayProfile profile = aggregator.aggregate(
                "12952", "KOTA", List.of(), Instant.parse("2026-09-09T11:00:00Z"));

        assertThat(profile.sampleCount()).isZero();
        assertThat(profile.source()).isEqualTo(DataProvenance.UNAVAILABLE);
    }

    @Test
    void theThreeArgOverloadDefaultsTheCutoffToNowViaTheInjectedClock() {
        // An observation recorded strictly after "now" (the fixed clock's instant) must be
        // excluded even though the 3-arg overload never receives an explicit cutoff.
        HistoricalObservation future = observationAt(Instant.parse("2026-09-10T10:00:00Z"), 999);

        HistoricalDelayProfile profile = aggregator.aggregate("12952", "KOTA", List.of(future));

        assertThat(profile.sampleCount()).isZero();
    }
}
