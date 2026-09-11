package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.TrainStationKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HistoricalDelayProfileRefreshSchedulerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);

    private final HistoricalObservationRepository observationRepository = mock(HistoricalObservationRepository.class);
    private final HistoricalDelayProfileRefresher refresher = mock(HistoricalDelayProfileRefresher.class);
    private final HistoricalSectionDelayProfileRefresher sectionRefresher = mock(HistoricalSectionDelayProfileRefresher.class);
    private final HistoricalDataMetrics metrics = new HistoricalDataMetrics();

    private static HistoricalDelayProfile profile(String trainNumber, String stationCode, int sampleCount) {
        return new HistoricalDelayProfile(
                trainNumber, stationCode, sampleCount, 10.0, 10.0, 0.0, DataProvenance.RAILRADAR, FIXED_CLOCK.instant());
    }

    private static HistoricalSectionDelayProfile sectionProfile(String trainNumber, String from, String to) {
        return new HistoricalSectionDelayProfile(
                trainNumber, from, to, 3, 5.0, 5.0, 0.0, DataProvenance.RAILRADAR, FIXED_CLOCK.instant());
    }

    private HistoricalDelayProfileRefreshScheduler scheduler(Optional<HistoricalObservationRepository> repository) {
        return new HistoricalDelayProfileRefreshScheduler(repository, refresher, sectionRefresher, metrics);
    }

    @Test
    void doesNothingWhenNoObservationRepositoryIsConfigured() {
        HistoricalDelayProfileRefreshScheduler scheduler = scheduler(Optional.empty());

        scheduler.refreshAllProfiles();

        verify(refresher, never()).refreshProfile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(sectionRefresher, never()).refreshSectionProfilesForTrain(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refreshesEveryDistinctTrainStationPair() {
        when(observationRepository.findDistinctTrainStationKeys())
                .thenReturn(List.of(new TrainStationKey("12952", "KOTA"), new TrainStationKey("12952", "RTM")));
        when(observationRepository.findDistinctTrainNumbers()).thenReturn(List.of());
        when(refresher.refreshProfile("12952", "KOTA")).thenReturn(profile("12952", "KOTA", 3));
        when(refresher.refreshProfile("12952", "RTM")).thenReturn(profile("12952", "RTM", 5));
        HistoricalDelayProfileRefreshScheduler scheduler = scheduler(Optional.of(observationRepository));

        scheduler.refreshAllProfiles();

        verify(refresher).refreshProfile("12952", "KOTA");
        verify(refresher).refreshProfile("12952", "RTM");
        assertThat(metrics.profilesGenerated()).isEqualTo(2);
        assertThat(metrics.profileRefreshFailures()).isZero();
    }

    @Test
    void onePairFailingDoesNotPreventTheOthersFromRefreshing() {
        when(observationRepository.findDistinctTrainStationKeys())
                .thenReturn(List.of(new TrainStationKey("12952", "KOTA"), new TrainStationKey("12952", "RTM")));
        when(observationRepository.findDistinctTrainNumbers()).thenReturn(List.of());
        when(refresher.refreshProfile("12952", "KOTA")).thenThrow(new RuntimeException("db hiccup"));
        when(refresher.refreshProfile("12952", "RTM")).thenReturn(profile("12952", "RTM", 5));
        HistoricalDelayProfileRefreshScheduler scheduler = scheduler(Optional.of(observationRepository));

        scheduler.refreshAllProfiles();

        verify(refresher).refreshProfile("12952", "RTM");
        assertThat(metrics.profilesGenerated()).isEqualTo(1);
        assertThat(metrics.profileRefreshFailures()).isEqualTo(1);
    }

    @Test
    void refreshesSectionProfilesForEveryDistinctTrainNumber() {
        when(observationRepository.findDistinctTrainStationKeys()).thenReturn(List.of());
        when(observationRepository.findDistinctTrainNumbers()).thenReturn(List.of("12952", "22222"));
        when(sectionRefresher.refreshSectionProfilesForTrain("12952"))
                .thenReturn(List.of(sectionProfile("12952", "A", "B")));
        when(sectionRefresher.refreshSectionProfilesForTrain("22222"))
                .thenReturn(List.of(sectionProfile("22222", "X", "Y")));
        HistoricalDelayProfileRefreshScheduler scheduler = scheduler(Optional.of(observationRepository));

        scheduler.refreshAllProfiles();

        verify(sectionRefresher).refreshSectionProfilesForTrain("12952");
        verify(sectionRefresher).refreshSectionProfilesForTrain("22222");
        assertThat(metrics.sectionProfilesGenerated()).isEqualTo(2);
        assertThat(metrics.sectionProfileRefreshFailures()).isZero();
    }

    @Test
    void oneTrainsSectionRefreshFailingDoesNotPreventTheOthersFromRefreshing() {
        when(observationRepository.findDistinctTrainStationKeys()).thenReturn(List.of());
        when(observationRepository.findDistinctTrainNumbers()).thenReturn(List.of("12952", "22222"));
        when(sectionRefresher.refreshSectionProfilesForTrain("12952")).thenThrow(new RuntimeException("db hiccup"));
        when(sectionRefresher.refreshSectionProfilesForTrain("22222"))
                .thenReturn(List.of(sectionProfile("22222", "X", "Y")));
        HistoricalDelayProfileRefreshScheduler scheduler = scheduler(Optional.of(observationRepository));

        scheduler.refreshAllProfiles();

        verify(sectionRefresher).refreshSectionProfilesForTrain("22222");
        assertThat(metrics.sectionProfilesGenerated()).isEqualTo(1);
        assertThat(metrics.sectionProfileRefreshFailures()).isEqualTo(1);
    }
}
