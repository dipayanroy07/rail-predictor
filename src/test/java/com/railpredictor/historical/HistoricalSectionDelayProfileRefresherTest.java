package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
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

class HistoricalSectionDelayProfileRefresherTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);

    private final HistoricalObservationRepository observationRepository = mock(HistoricalObservationRepository.class);
    private final HistoricalSectionDelayProfileRepository profileRepository = mock(HistoricalSectionDelayProfileRepository.class);
    private final HistoricalObservationEntityMapper observationEntityMapper = new HistoricalObservationEntityMapper();
    private final HistoricalSectionDelayProfileEntityMapper profileEntityMapper = new HistoricalSectionDelayProfileEntityMapper();
    private final HistoricalSectionDelayProfileAggregator aggregator = new HistoricalSectionDelayProfileAggregator(FIXED_CLOCK);

    private HistoricalSectionDelayProfileRefresher refresherWithBothRepositories() {
        return new HistoricalSectionDelayProfileRefresher(
                Optional.of(observationRepository), Optional.of(profileRepository),
                observationEntityMapper, profileEntityMapper, aggregator, FIXED_CLOCK);
    }

    private static HistoricalObservationEntity entity(
            String trainNumber, LocalDate journeyDate, String stationCode, Integer sequence,
            Integer arrivalDelay, Integer departureDelay) {
        return new HistoricalObservationEntity(
                trainNumber, journeyDate, stationCode, sequence,
                null, arrivalDelay == null ? null : "20:00", null, departureDelay == null ? null : "20:05",
                arrivalDelay, departureDelay, Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);
    }

    @Test
    void throwsWhenNoObservationRepositoryIsConfigured() {
        HistoricalSectionDelayProfileRefresher refresher = new HistoricalSectionDelayProfileRefresher(
                Optional.empty(), Optional.of(profileRepository),
                observationEntityMapper, profileEntityMapper, aggregator, FIXED_CLOCK);

        assertThrows(IllegalStateException.class, () -> refresher.refreshSectionProfilesForTrain("12952"));
    }

    @Test
    void throwsWhenNoProfileRepositoryIsConfigured() {
        HistoricalSectionDelayProfileRefresher refresher = new HistoricalSectionDelayProfileRefresher(
                Optional.of(observationRepository), Optional.empty(),
                observationEntityMapper, profileEntityMapper, aggregator, FIXED_CLOCK);

        assertThrows(IllegalStateException.class, () -> refresher.refreshSectionProfilesForTrain("12952"));
    }

    @Test
    void aggregatesEveryDiscoveredSectionAndInsertsNewProfiles() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of(
                entity("12952", date, "A", 1, null, 0),
                entity("12952", date, "B", 2, 5, 5)));
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "A", "B"))
                .thenReturn(Optional.empty());

        List<HistoricalSectionDelayProfile> profiles = refresherWithBothRepositories().refreshSectionProfilesForTrain("12952");

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).sampleCount()).isEqualTo(1);
        verify(profileRepository).save(any(HistoricalSectionDelayProfileEntity.class));
    }

    @Test
    void reRefreshingUpdatesTheExistingSectionProfileRowInsteadOfInsertingASecondOne() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of(
                entity("12952", date, "A", 1, null, 0),
                entity("12952", date, "B", 2, 5, 5)));
        HistoricalSectionDelayProfileEntity existing = new HistoricalSectionDelayProfileEntity(
                "12952", "A", "B", 1, 1.0, 1.0, 0.0, DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
        when(profileRepository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "A", "B"))
                .thenReturn(Optional.of(existing));

        refresherWithBothRepositories().refreshSectionProfilesForTrain("12952");

        verify(profileRepository, times(1)).save(existing);
        assertThat(existing.getAverageDelayChangeMinutes()).isEqualTo(5.0);
    }

    @Test
    void noObservationsProduceNoSectionProfiles() {
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of());

        List<HistoricalSectionDelayProfile> profiles = refresherWithBothRepositories().refreshSectionProfilesForTrain("12952");

        assertThat(profiles).isEmpty();
    }

    @Test
    void aReferenceInstantOverloadIsHonoured() {
        LocalDate date = LocalDate.of(2026, 9, 9);
        when(observationRepository.findByTrainNumber("12952")).thenReturn(List.of(
                entity("12952", date, "A", 1, null, 0),
                entity("12952", date, "B", 2, 5, 5)));

        List<HistoricalSectionDelayProfile> profiles = refresherWithBothRepositories()
                .refreshSectionProfilesForTrain("12952", Instant.parse("2000-01-01T00:00:00Z"));

        // Every observation's observedAt (2026-09-09) is after this early cutoff, so nothing
        // qualifies - proving the overload's cutoff is actually applied, not ignored.
        assertThat(profiles).isEmpty();
    }
}
