package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import com.railpredictor.repository.HistoricalDelayProfileEntity;
import com.railpredictor.repository.HistoricalDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalDelayProfileRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostgresHistoricalDelayProviderTest {

    // 2026-09-09 is a Wednesday in September.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    private final HistoricalDelayProfileRepository profileRepository = mock(HistoricalDelayProfileRepository.class);
    private final PostgresHistoricalDelayProvider provider = new PostgresHistoricalDelayProvider(
            profileRepository, new HistoricalDelayProfileEntityMapper(), FIXED_CLOCK);

    private static RouteSection section() {
        return new RouteSection(new Station("NDLS", "New Delhi"), new Station("KOTA", "Kota Jn"), null);
    }

    @Test
    void looksUpTheProfileByTheSectionsToStationNotFromStation() {
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.empty());

        provider.getHistoricalDelay("12952", section());

        org.mockito.Mockito.verify(profileRepository).findByTrainNumberAndStationCode("12952", "KOTA");
        org.mockito.Mockito.verify(profileRepository, org.mockito.Mockito.never())
                .findByTrainNumberAndStationCode("12952", "NDLS");
    }

    @Test
    void returnsANoDataResultWhenNoProfileExists() {
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.empty());

        HistoricalDelay result = provider.getHistoricalDelay("12952", section());

        assertThat(result.sampleCount()).isZero();
        assertThat(result.source()).isEqualTo(DataProvenance.UNAVAILABLE);
        assertThat(result.section()).isEqualTo(section());
        assertThat(result.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(result.month()).isEqualTo(Month.SEPTEMBER);
    }

    @Test
    void adaptsAnExistingProfileIntoHistoricalDelay() {
        HistoricalDelayProfileEntity entity = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 10, 8.5, 6.0, 3.25, DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.of(entity));

        HistoricalDelay result = provider.getHistoricalDelay("12952", section());

        assertThat(result.sampleCount()).isEqualTo(10);
        assertThat(result.averageDelayMinutes()).isEqualTo(8.5);
        assertThat(result.medianDelayMinutes()).isEqualTo(6.0);
        assertThat(result.standardDeviationMinutes()).isEqualTo(3.25);
        assertThat(result.source()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void clampsANegativeProfileAverageAndMedianToZeroForHistoricalDelaysStricterContract() {
        HistoricalDelayProfileEntity entity = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 2, -3.0, -3.0, 1.0, DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.of(entity));

        HistoricalDelay result = provider.getHistoricalDelay("12952", section());

        assertThat(result.averageDelayMinutes()).isEqualTo(0.0);
        assertThat(result.medianDelayMinutes()).isEqualTo(0.0);
        // standard deviation is a magnitude, never negative to begin with - untouched.
        assertThat(result.standardDeviationMinutes()).isEqualTo(1.0);
    }

    @Test
    void passesThroughAnInsufficientSampleCountUnmodifiedRatherThanFabricatingTrust() {
        // Below PredictionHistoricalAdjustmentProperties' minimum-sample-count default (5) -
        // the provider must hand this over as-is; only HistoricalDelayCalculator decides trust.
        HistoricalDelayProfileEntity entity = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 1, 4.0, 4.0, 0.0, DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.of(entity));

        HistoricalDelay result = provider.getHistoricalDelay("12952", section());

        assertThat(result.sampleCount()).isEqualTo(1);
        assertThat(result.averageDelayMinutes()).isEqualTo(4.0);
        assertThat(result.source()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void passesThroughAMixedProvenanceLabelUnchanged() {
        HistoricalDelayProfileEntity entity = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 2, 8.0, 8.0, 1.0, "mixed(mock-provider,railradar)", Instant.parse("2026-09-09T09:00:00Z"));
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.of(entity));

        HistoricalDelay result = provider.getHistoricalDelay("12952", section());

        assertThat(result.source()).isEqualTo("mixed(mock-provider,railradar)");
    }
}
