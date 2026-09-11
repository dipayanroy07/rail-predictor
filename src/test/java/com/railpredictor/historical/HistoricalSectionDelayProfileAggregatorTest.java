package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HistoricalSectionDelayProfileAggregatorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");

    private final HistoricalSectionDelayProfileAggregator aggregator = new HistoricalSectionDelayProfileAggregator(FIXED_CLOCK);

    private static HistoricalObservation observation(
            String trainNumber, LocalDate journeyDate, String stationCode, Integer sequence,
            Integer arrivalDelay, Integer departureDelay, Instant observedAt, String source) {
        return new HistoricalObservation(
                trainNumber, journeyDate, stationCode, sequence,
                null, arrivalDelay == null ? null : "20:00", null, departureDelay == null ? null : "20:05",
                arrivalDelay, departureDelay, observedAt, source);
    }

    private static HistoricalObservation observation(
            String trainNumber, LocalDate journeyDate, String stationCode, Integer sequence,
            Integer arrivalDelay, Integer departureDelay) {
        return observation(trainNumber, journeyDate, stationCode, sequence, arrivalDelay, departureDelay,
                Instant.parse("2026-09-09T12:00:00Z"), DataProvenance.RAILRADAR);
    }

    private static Optional<HistoricalSectionDelayProfile> find(
            List<HistoricalSectionDelayProfile> profiles, String from, String to) {
        return profiles.stream()
                .filter(p -> p.fromStationCode().equals(from) && p.toStationCode().equals(to))
                .findFirst();
    }

    // --- Pairing ---

    @Test
    void chainsThreeConsecutiveStationsIntoTwoSections() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0),
                observation("12952", date, "B", 2, 5, 5),
                observation("12952", date, "C", 3, 8, null));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        assertThat(profiles).hasSize(2);
        assertThat(find(profiles, "A", "B")).isPresent();
        assertThat(find(profiles, "B", "C")).isPresent();
        assertThat(find(profiles, "A", "B").get().sampleCount()).isEqualTo(1);
        assertThat(find(profiles, "A", "B").get().averageDelayChangeMinutes()).isEqualTo(5.0);
    }

    @Test
    void multipleJourneysOfTheSameTrainContributeToTheSameSectionProfile() {
        List<HistoricalObservation> observations = List.of(
                observation("12952", LocalDate.of(2026, 9, 8), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 8), "B", 2, 10, 10),
                observation("12952", LocalDate.of(2026, 9, 9), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 9), "B", 2, 20, 20));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        HistoricalSectionDelayProfile ab = find(profiles, "A", "B").orElseThrow();
        assertThat(ab.sampleCount()).isEqualTo(2);
        assertThat(ab.averageDelayChangeMinutes()).isEqualTo(15.0);
    }

    @Test
    void sameTrainOnDifferentDatesAreNeverCrossPaired() {
        // Only same-journeyDate observations may be paired - a station from one journeyDate must
        // never pair with a station from a different journeyDate, even for the same train.
        List<HistoricalObservation> observations = List.of(
                observation("12952", LocalDate.of(2026, 9, 8), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 9), "B", 2, 10, 10));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        assertThat(profiles).isEmpty();
    }

    @Test
    void repeatedStationCodeWithDistinctValidSequencesProducesCorrectAdjacentPairs() {
        // A loop route: station "A" appears twice in the same journey at different sequences.
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0),
                observation("12952", date, "B", 2, 5, 5),
                observation("12952", date, "A", 3, 8, 8));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        assertThat(find(profiles, "A", "B")).isPresent();
        assertThat(find(profiles, "B", "A")).isPresent();
    }

    @Test
    void nullSequenceObservationsNeverParticipateInAnyPair() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0),
                observation("12952", date, "B", null, 5, 5),
                observation("12952", date, "C", 3, 8, 8));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        // B has a null sequence, so it can never be a FROM or a TO - only A and C remain, but
        // they are not consecutive in the sequence-sorted (non-null) list... actually A(1) and
        // C(3) ARE the only two non-null-sequence entries, so they pair directly.
        assertThat(profiles).hasSize(1);
        assertThat(find(profiles, "A", "C")).isPresent();
        assertThat(find(profiles, "A", "B")).isEmpty();
        assertThat(find(profiles, "B", "C")).isEmpty();
    }

    @Test
    void aSequenceGapIsPairedAcrossWithoutInferringACause() {
        // Sequence 2 is simply missing (skipped/cancelled/never observed - indistinguishable) -
        // the aggregator pairs the two observations that ARE present (1 and 3) directly.
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0),
                observation("12952", date, "C", 3, 8, 8));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        assertThat(profiles).hasSize(1);
        assertThat(find(profiles, "A", "C")).isPresent();
    }

    @Test
    void unsortedInputObservationsAreOrderedBySequenceNotByListOrder() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "C", 3, 8, 8),
                observation("12952", date, "A", 1, null, 0),
                observation("12952", date, "B", 2, 5, 5));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        assertThat(find(profiles, "A", "B")).isPresent();
        assertThat(find(profiles, "B", "C")).isPresent();
        assertThat(find(profiles, "C", "A")).isEmpty();
    }

    @Test
    void ambiguousDuplicateSequenceValuesExcludeTheWholeJourneyFromPairing() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0),
                observation("12952", date, "B", 2, 5, 5),
                observation("12952", date, "X", 2, 9, 9), // duplicate sequence 2 - ambiguous
                observation("12952", date, "C", 3, 8, 8));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        assertThat(profiles).isEmpty();
    }

    @Test
    void anAdjacentPairMissingEitherEndpointDelayStillProducesAZeroSampleProfileNotNoProfile() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, null), // no departure delay
                observation("12952", date, "B", 2, 5, 5));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        HistoricalSectionDelayProfile ab = find(profiles, "A", "B").orElseThrow();
        assertThat(ab.sampleCount()).isZero();
        assertThat(ab.source()).isEqualTo(DataProvenance.UNAVAILABLE);
    }

    @Test
    void originDepartureToNextStationArrivalFormsAValidSample() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "NDLS", 1, null, 5), // origin: no arrival, departure delay 5
                observation("12952", date, "KOTA", 2, 12, 12));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, FAR_FUTURE);

        HistoricalSectionDelayProfile section = find(profiles, "NDLS", "KOTA").orElseThrow();
        assertThat(section.sampleCount()).isEqualTo(1);
        assertThat(section.averageDelayChangeMinutes()).isEqualTo(7.0); // 12 - 5
    }

    // --- Calculation ---

    @Test
    void positiveDelayChangeIsPreserved() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 10),
                observation("12952", date, "B", 2, 15, 15));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.averageDelayChangeMinutes()).isEqualTo(5.0);
    }

    @Test
    void negativeDelayChangeIsPreservedNotClamped() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 15),
                observation("12952", date, "B", 2, 11, 11));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.averageDelayChangeMinutes()).isEqualTo(-4.0);
    }

    @Test
    void zeroDelayChangeIsPreserved() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 10),
                observation("12952", date, "B", 2, 10, 10));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.averageDelayChangeMinutes()).isEqualTo(0.0);
    }

    @Test
    void computesMeanMedianAndSampleStandardDeviationOverMultipleSamples() {
        List<HistoricalObservation> observations = List.of(
                observation("12952", LocalDate.of(2026, 9, 1), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 1), "B", 2, 5, 5),
                observation("12952", LocalDate.of(2026, 9, 2), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 2), "B", 2, 15, 15),
                observation("12952", LocalDate.of(2026, 9, 3), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 3), "B", 2, 10, 10));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        // delay changes: 5, 15, 10 -> mean 10, median 10 (odd count -> middle of [5,10,15])
        assertThat(ab.sampleCount()).isEqualTo(3);
        assertThat(ab.averageDelayChangeMinutes()).isEqualTo(10.0);
        assertThat(ab.medianDelayChangeMinutes()).isEqualTo(10.0);
        assertThat(ab.standardDeviationMinutes()).isGreaterThan(0.0);
        assertThat(ab.standardDeviationMinutes()).isFinite();
    }

    @Test
    void evenSampleCountMedianIsTheAverageOfTheTwoMiddleValues() {
        List<HistoricalObservation> observations = List.of(
                observation("12952", LocalDate.of(2026, 9, 1), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 1), "B", 2, 4, 4), // delay change 4
                observation("12952", LocalDate.of(2026, 9, 2), "A", 1, null, 0),
                observation("12952", LocalDate.of(2026, 9, 2), "B", 2, 10, 10)); // delay change 10

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.medianDelayChangeMinutes()).isEqualTo(7.0);
    }

    @Test
    void oneSampleProducesZeroStandardDeviationNotNaN() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 10),
                observation("12952", date, "B", 2, 15, 15));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.sampleCount()).isEqualTo(1);
        assertThat(ab.standardDeviationMinutes()).isEqualTo(0.0);
    }

    @Test
    void zeroObservationsProduceNoProfilesAtAll() {
        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", List.of(), FAR_FUTURE);

        assertThat(profiles).isEmpty();
    }

    // --- Provenance ---

    @Test
    void realPlusRealCombinesToRailradar() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0, Instant.parse("2026-09-09T10:00:00Z"), DataProvenance.RAILRADAR),
                observation("12952", date, "B", 2, 5, 5, Instant.parse("2026-09-09T10:05:00Z"), DataProvenance.RAILRADAR));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.source()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void mockPlusMockCombinesToMock() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0, Instant.parse("2026-09-09T10:00:00Z"), DataProvenance.MOCK),
                observation("12952", date, "B", 2, 5, 5, Instant.parse("2026-09-09T10:05:00Z"), DataProvenance.MOCK));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.source()).isEqualTo(DataProvenance.MOCK);
    }

    @Test
    void realPlusMockNeverSilentlyCollapsesToARealLookingSource() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0, Instant.parse("2026-09-09T10:00:00Z"), DataProvenance.MOCK),
                observation("12952", date, "B", 2, 5, 5, Instant.parse("2026-09-09T10:05:00Z"), DataProvenance.RAILRADAR));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.source()).isEqualTo(DataProvenance.combine(java.util.Set.of(DataProvenance.MOCK, DataProvenance.RAILRADAR)));
        assertThat(ab.source()).contains("mixed(");
    }

    @Test
    void multipleSamplesWithMixedProvenanceAcrossSamplesStillReportsAnExplicitMixedLabel() {
        List<HistoricalObservation> observations = List.of(
                observation("12952", LocalDate.of(2026, 9, 1), "A", 1, null, 0,
                        Instant.parse("2026-09-01T10:00:00Z"), DataProvenance.RAILRADAR),
                observation("12952", LocalDate.of(2026, 9, 1), "B", 2, 5, 5,
                        Instant.parse("2026-09-01T10:05:00Z"), DataProvenance.RAILRADAR),
                observation("12952", LocalDate.of(2026, 9, 2), "A", 1, null, 0,
                        Instant.parse("2026-09-02T10:00:00Z"), DataProvenance.MOCK),
                observation("12952", LocalDate.of(2026, 9, 2), "B", 2, 10, 10,
                        Instant.parse("2026-09-02T10:05:00Z"), DataProvenance.MOCK));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, FAR_FUTURE), "A", "B").orElseThrow();

        assertThat(ab.sampleCount()).isEqualTo(2);
        assertThat(ab.source()).contains("mixed(");
        assertThat(ab.source()).contains(DataProvenance.MOCK).contains(DataProvenance.RAILRADAR);
    }

    // --- Temporal cutoff ---

    @Test
    void anObservationBeforeTheCutoffIsIncluded() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        Instant cutoff = Instant.parse("2026-09-09T12:00:00Z");
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0, Instant.parse("2026-09-09T11:00:00Z"), DataProvenance.RAILRADAR),
                observation("12952", date, "B", 2, 5, 5, Instant.parse("2026-09-09T11:05:00Z"), DataProvenance.RAILRADAR));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, cutoff), "A", "B").orElseThrow();

        assertThat(ab.sampleCount()).isEqualTo(1);
    }

    @Test
    void anObservationExactlyAtTheCutoffIsIncluded() {
        // Chosen semantics: the cutoff instant itself is inclusive.
        LocalDate date = LocalDate.of(2026, 9, 9);
        Instant cutoff = Instant.parse("2026-09-09T11:05:00Z");
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0, Instant.parse("2026-09-09T11:00:00Z"), DataProvenance.RAILRADAR),
                observation("12952", date, "B", 2, 5, 5, cutoff, DataProvenance.RAILRADAR));

        HistoricalSectionDelayProfile ab = find(
                aggregator.aggregate("12952", observations, cutoff), "A", "B").orElseThrow();

        assertThat(ab.sampleCount()).isEqualTo(1);
    }

    @Test
    void anObservationAfterTheCutoffIsExcludedEntirely() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        Instant cutoff = Instant.parse("2026-09-09T11:00:00Z");
        List<HistoricalObservation> observations = List.of(
                observation("12952", date, "A", 1, null, 0, Instant.parse("2026-09-09T10:00:00Z"), DataProvenance.RAILRADAR),
                observation("12952", date, "B", 2, 5, 5, Instant.parse("2026-09-09T11:05:00Z"), DataProvenance.RAILRADAR));

        List<HistoricalSectionDelayProfile> profiles = aggregator.aggregate("12952", observations, cutoff);

        // B was recorded after the cutoff, so A can no longer be paired with anything - no
        // profile for (A, B) should be produced at all (the adjacency itself isn't in scope).
        assertThat(find(profiles, "A", "B")).isEmpty();
    }
}
