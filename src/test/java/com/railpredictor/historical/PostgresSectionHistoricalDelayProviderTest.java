package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.config.HistoricalSectionProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import com.railpredictor.model.domain.Station;
import com.railpredictor.repository.HistoricalObservationEntity;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.HistoricalSectionDelayProfileEntity;
import com.railpredictor.repository.HistoricalSectionDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalSectionDelayProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostgresSectionHistoricalDelayProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

    private final HistoricalSectionDelayProfileRepository profileRepository = mock(HistoricalSectionDelayProfileRepository.class);
    private final HistoricalObservationRepository observationRepository = mock(HistoricalObservationRepository.class);
    private final HistoricalSectionDelayProfileEntityMapper profileEntityMapper = new HistoricalSectionDelayProfileEntityMapper();
    private final HistoricalObservationEntityMapper observationEntityMapper = new HistoricalObservationEntityMapper();
    private final HistoricalSectionDelayProfileAggregator aggregator = new HistoricalSectionDelayProfileAggregator(FIXED_CLOCK);

    private PostgresSectionHistoricalDelayProvider provider(int minimumSampleCount) {
        return new PostgresSectionHistoricalDelayProvider(
                profileRepository, profileEntityMapper, observationRepository, observationEntityMapper,
                aggregator, new HistoricalSectionProperties(minimumSampleCount), FIXED_CLOCK);
    }

    private static RouteSection section(String from, String to) {
        return new RouteSection(new Station(from, from + " Station"), new Station(to, to + " Station"), null);
    }

    private static HistoricalSectionDelayProfileEntity profileEntity(
            String trainNumber, String from, String to, int sampleCount, double average, String source) {
        return new HistoricalSectionDelayProfileEntity(
                trainNumber, from, to, sampleCount, average, average, 1.0, source, Instant.parse("2026-09-10T08:00:00Z"));
    }

    // --- Cache path (referenceInstant is "now" or later) ---

    @Test
    void looksUpTheCorrectStationPairForANowOrLaterReferenceInstant() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.of(profileEntity("12952", "NDLS", "KOTA", 15, 4.0, DataProvenance.RAILRADAR)));

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.AVAILABLE);
        assertThat(result.profile().averageDelayChangeMinutes()).isEqualTo(4.0);
        verify(observationRepository, never()).findByTrainNumber(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsNotFoundWhenNoProfileExistsForTheRequestedPair() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.empty());

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
        assertThat(result.profile()).isNull();
    }

    @Test
    void aWrongTrainNumberIsNeverMatched() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("99999", "NDLS", "KOTA"))
                .thenReturn(Optional.empty());

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("99999", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
        verify(profileRepository).findByTrainNumberAndFromStationCodeAndToStationCode("99999", "NDLS", "KOTA");
    }

    @Test
    void aWrongFromStationIsNeverMatched() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "WRONG", "KOTA"))
                .thenReturn(Optional.empty());

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("WRONG", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
    }

    @Test
    void aWrongToStationIsNeverMatched() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "WRONG"))
                .thenReturn(Optional.empty());

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "WRONG"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
    }

    @Test
    void reportsInsufficientSamplesWhenBelowTheConfiguredThreshold() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.of(profileEntity("12952", "NDLS", "KOTA", 3, 4.0, DataProvenance.RAILRADAR)));

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES);
        assertThat(result.profile().sampleCount()).isEqualTo(3);
    }

    @Test
    void negativeAverageDelayChangeIsPreservedNotClamped() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.of(profileEntity("12952", "NDLS", "KOTA", 15, -6.0, DataProvenance.RAILRADAR)));

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.profile().averageDelayChangeMinutes()).isEqualTo(-6.0);
    }

    @Test
    void zeroDelayChangeIsPreserved() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.of(profileEntity("12952", "NDLS", "KOTA", 15, 0.0, DataProvenance.RAILRADAR)));

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.profile().averageDelayChangeMinutes()).isEqualTo(0.0);
    }

    @Test
    void mockOrMixedProvenanceIsPassedThroughUnhiddenAndUnrejected() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.of(profileEntity(
                        "12952", "NDLS", "KOTA", 15, 4.0, "mixed(mock-provider,railradar)")));

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.AVAILABLE);
        assertThat(result.profile().source()).isEqualTo("mixed(mock-provider,railradar)");
    }

    @Test
    void domainResultNeverExposesAJpaEntityOrRailRadarType() {
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "NDLS", "KOTA"))
                .thenReturn(Optional.of(profileEntity("12952", "NDLS", "KOTA", 15, 4.0, DataProvenance.RAILRADAR)));

        SectionHistoricalDelayResult result =
                provider(10).getSectionDelay("12952", section("NDLS", "KOTA"), Instant.now(FIXED_CLOCK));

        assertThat(result.getClass().getPackageName()).isEqualTo("com.railpredictor.model.domain");
        assertThat(result.profile().getClass().getPackageName()).isEqualTo("com.railpredictor.model.domain");
    }

    // --- Temporal semantics: proving what the cache CAN and CANNOT safely answer ---

    @Test
    void aReferenceInstantStrictlyInThePastBypassesTheCacheEntirelyAndReAggregatesFromRawObservations() {
        // Even though a cached row exists (and would misleadingly answer "now"), a genuinely
        // retrospective request must never trust it - proves the on-demand path is actually taken.
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(profileEntity("12952", "NDLS", "KOTA", 999, 999.0, DataProvenance.RAILRADAR)));
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of(
                observationEntity("12952", LocalDate.of(2026, 9, 1), "NDLS", 1, null, 0, Instant.parse("2026-09-01T10:00:00Z")),
                observationEntity("12952", LocalDate.of(2026, 9, 1), "KOTA", 2, 5, 5, Instant.parse("2026-09-01T11:00:00Z"))));

        SectionHistoricalDelayResult result = provider(1).getSectionDelay(
                "12952", section("NDLS", "KOTA"), Instant.parse("2026-09-05T00:00:00Z"));

        verify(profileRepository, never()).findByTrainNumberAndFromStationCodeAndToStationCode(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.AVAILABLE);
        // The re-aggregated value (5) is the honest one - not the misleading cached value (999)
        // that a naive cache-trusting implementation would have returned instead.
        assertThat(result.profile().averageDelayChangeMinutes()).isEqualTo(5.0);
    }

    @Test
    void aPastReferenceInstantExcludesObservationsRecordedAfterIt() {
        // Demonstrates the on-demand path's own correctness: an observation recorded after the
        // requested cutoff must not influence the result.
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of(
                observationEntity("12952", LocalDate.of(2026, 9, 1), "NDLS", 1, null, 0, Instant.parse("2026-09-01T10:00:00Z")),
                observationEntity("12952", LocalDate.of(2026, 9, 1), "KOTA", 2, 5, 5, Instant.parse("2026-09-01T11:00:00Z"))));

        // Cutoff before KOTA's observedAt - the adjacency can't be completed, so no profile at all.
        SectionHistoricalDelayResult result = provider(1).getSectionDelay(
                "12952", section("NDLS", "KOTA"), Instant.parse("2026-09-01T10:30:00Z"));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
    }

    @Test
    void demonstratesTheMaterializedCacheCannotAloneAnswerAGenuinePointInTimeQuery() {
        // Design-note-as-test (Phase 16G item 6/9): the cached row's own computedAt long postdates
        // the requested historical cutoff, and the cache carries no version history - so a naive
        // "just read the cache" implementation would have returned data reflecting observations
        // recorded long after the requested moment (a real leakage risk). This test exists to prove
        // the provider does NOT do that: it re-derives from raw data instead, honestly excluding
        // anything not yet known as of the requested cutoff. See
        // aReferenceInstantStrictlyInThePastBypassesTheCacheEntirelyAndReAggregatesFromRawObservations
        // above for the same proof from the opposite angle (showing the correct value is returned,
        // not just that the cache is skipped).
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of());

        SectionHistoricalDelayResult result = provider(1).getSectionDelay(
                "12952", section("NDLS", "KOTA"), Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.NOT_FOUND);
        verify(profileRepository, never()).findByTrainNumberAndFromStationCodeAndToStationCode(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static HistoricalObservationEntity observationEntity(
            String trainNumber, LocalDate journeyDate, String stationCode, Integer sequence,
            Integer arrivalDelay, Integer departureDelay, Instant observedAt) {
        return new HistoricalObservationEntity(
                trainNumber, journeyDate, stationCode, sequence,
                null, arrivalDelay == null ? null : "20:00", null, departureDelay == null ? null : "20:05",
                arrivalDelay, departureDelay, observedAt, DataProvenance.RAILRADAR);
    }
}
