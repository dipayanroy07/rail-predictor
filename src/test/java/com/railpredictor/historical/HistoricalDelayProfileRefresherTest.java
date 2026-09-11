package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.repository.HistoricalDelayProfileEntity;
import com.railpredictor.repository.HistoricalDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalDelayProfileRepository;
import com.railpredictor.repository.HistoricalObservationEntity;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HistoricalDelayProfileRefresherTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);

    private final HistoricalObservationRepository observationRepository = mock(HistoricalObservationRepository.class);
    private final HistoricalDelayProfileRepository profileRepository = mock(HistoricalDelayProfileRepository.class);
    private final HistoricalObservationEntityMapper observationEntityMapper = new HistoricalObservationEntityMapper();
    private final HistoricalDelayProfileEntityMapper profileEntityMapper = new HistoricalDelayProfileEntityMapper();
    private final HistoricalDelayProfileAggregator aggregator = new HistoricalDelayProfileAggregator(FIXED_CLOCK);

    private HistoricalDelayProfileRefresher refresherWithBothRepositories() {
        return new HistoricalDelayProfileRefresher(
                Optional.of(observationRepository), Optional.of(profileRepository),
                observationEntityMapper, profileEntityMapper, aggregator);
    }

    private static HistoricalObservationEntity observationEntity(int arrivalDelayMinutes) {
        return new HistoricalObservationEntity(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5,
                "20:39", "20:51", "20:41", "20:53", arrivalDelayMinutes, arrivalDelayMinutes,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);
    }

    @Test
    void throwsWhenNoObservationRepositoryIsConfigured() {
        HistoricalDelayProfileRefresher refresher = new HistoricalDelayProfileRefresher(
                Optional.empty(), Optional.of(profileRepository), observationEntityMapper, profileEntityMapper, aggregator);

        assertThrows(IllegalStateException.class, () -> refresher.refreshProfile("12952", "KOTA"));
    }

    @Test
    void throwsWhenNoProfileRepositoryIsConfigured() {
        HistoricalDelayProfileRefresher refresher = new HistoricalDelayProfileRefresher(
                Optional.of(observationRepository), Optional.empty(), observationEntityMapper, profileEntityMapper, aggregator);

        assertThrows(IllegalStateException.class, () -> refresher.refreshProfile("12952", "KOTA"));
    }

    @Test
    void aggregatesTheLoadedObservationsAndInsertsANewProfileWhenNoneExisted() {
        when(observationRepository.findByTrainNumberAndStationCode("12952", "KOTA"))
                .thenReturn(List.of(observationEntity(10), observationEntity(20)));
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.empty());

        HistoricalDelayProfile profile = refresherWithBothRepositories().refreshProfile("12952", "KOTA");

        assertThat(profile.sampleCount()).isEqualTo(2);
        assertThat(profile.averageArrivalDelayMinutes()).isEqualTo(15.0);
        verify(profileRepository).save(any(HistoricalDelayProfileEntity.class));
    }

    @Test
    void reRefreshingUpdatesTheExistingProfileRowInsteadOfInsertingASecondOne() {
        when(observationRepository.findByTrainNumberAndStationCode("12952", "KOTA"))
                .thenReturn(List.of(observationEntity(10)));
        HistoricalDelayProfileEntity existing = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 1, 5.0, 5.0, 0.0, DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.of(existing));

        refresherWithBothRepositories().refreshProfile("12952", "KOTA");

        verify(profileRepository, times(1)).save(existing);
        assertThat(existing.getAverageArrivalDelayMinutes()).isEqualTo(10.0);
    }

    @Test
    void emptyObservationListProducesAZeroSampleProfileRatherThanFailing() {
        when(observationRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(List.of());
        when(profileRepository.findByTrainNumberAndStationCode("12952", "KOTA")).thenReturn(Optional.empty());

        HistoricalDelayProfile profile = refresherWithBothRepositories().refreshProfile("12952", "KOTA");

        assertThat(profile.sampleCount()).isZero();
        verify(profileRepository).save(any(HistoricalDelayProfileEntity.class));
    }
}
