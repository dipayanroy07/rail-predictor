package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.railpredictor.config.HistoricalCollectionProperties;
import com.railpredictor.exception.RailRadarUnavailableException;
import com.railpredictor.exception.TrainNotFoundException;
import com.railpredictor.railradar.TrainDataProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HistoricalObservationCollectionService} purely through the existing
 * {@link TrainDataProvider} seam - proves the service reuses the real fetch-and-record pipeline
 * (never a duplicated RailRadar client) and never touches anything prediction-related.
 */
class HistoricalObservationCollectionServiceTest {

    private static HistoricalCollectionProperties properties(String... trainNumbers) {
        return new HistoricalCollectionProperties(true, 3600000, 300000, List.of(trainNumbers));
    }

    @Test
    void emptyConfiguredListMakesNoRequestsAtAll() {
        TrainDataProvider trainDataProvider = mock(TrainDataProvider.class);
        HistoricalObservationCollectionService service =
                new HistoricalObservationCollectionService(trainDataProvider, properties());

        HistoricalObservationCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isZero();
        assertThat(result.trainsSucceeded()).isZero();
        assertThat(result.trainsFailed()).isZero();
        verifyNoInteractions(trainDataProvider);
    }

    @Test
    void aSuccessfulFetchReachesTheExistingTrainDataProviderForEachConfiguredTrain() {
        TrainDataProvider trainDataProvider = mock(TrainDataProvider.class);
        HistoricalObservationCollectionService service =
                new HistoricalObservationCollectionService(trainDataProvider, properties("12952", "12002"));

        HistoricalObservationCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isEqualTo(2);
        assertThat(result.trainsSucceeded()).isEqualTo(2);
        assertThat(result.trainsFailed()).isZero();
        verify(trainDataProvider, times(1)).getLiveTrainData(eq("12952"));
        verify(trainDataProvider, times(1)).getLiveTrainData(eq("12002"));
    }

    @Test
    void oneTrainFailureDoesNotPreventTheOthersFromBeingAttempted() {
        TrainDataProvider trainDataProvider = mock(TrainDataProvider.class);
        when(trainDataProvider.getLiveTrainData("12952")).thenThrow(new TrainNotFoundException("12952"));
        when(trainDataProvider.getLiveTrainData("12002")).thenThrow(new RailRadarUnavailableException("down"));
        when(trainDataProvider.getLiveTrainData("12919")).thenReturn(null);
        HistoricalObservationCollectionService service = new HistoricalObservationCollectionService(
                trainDataProvider, properties("12952", "12002", "12919"));

        HistoricalObservationCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isEqualTo(3);
        assertThat(result.trainsSucceeded()).isEqualTo(1);
        assertThat(result.trainsFailed()).isEqualTo(2);
        verify(trainDataProvider).getLiveTrainData("12952");
        verify(trainDataProvider).getLiveTrainData("12002");
        verify(trainDataProvider).getLiveTrainData("12919");
    }

    @Test
    void neverThrowsEvenWhenEveryConfiguredTrainFails() {
        TrainDataProvider trainDataProvider = mock(TrainDataProvider.class);
        when(trainDataProvider.getLiveTrainData("12952")).thenThrow(new RailRadarUnavailableException("down"));
        HistoricalObservationCollectionService service =
                new HistoricalObservationCollectionService(trainDataProvider, properties("12952"));

        HistoricalObservationCollectionResult result = service.collectAll();

        assertThat(result.trainsFailed()).isEqualTo(1);
        assertThat(result.trainsSucceeded()).isZero();
    }
}
