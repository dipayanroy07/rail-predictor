package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.railpredictor.config.HistoricalCollectionProperties;
import com.railpredictor.exception.PredictionNotApplicableException;
import com.railpredictor.exception.RailRadarUnavailableException;
import com.railpredictor.service.PredictionService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PredictionCollectionService} purely through the existing
 * {@link PredictionService} seam - proves the service reuses the real prediction pipeline (never
 * a duplicated prediction/snapshot-creation code path) and treats "no next station" as an honest,
 * non-failure outcome distinct from a genuine failure.
 */
class PredictionCollectionServiceTest {

    private static HistoricalCollectionProperties trainList(String... trainNumbers) {
        return new HistoricalCollectionProperties(true, 3600000, 300000, List.of(trainNumbers));
    }

    @Test
    void emptyConfiguredListMakesNoRequestsAtAll() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionCollectionService service = new PredictionCollectionService(predictionService, trainList());

        PredictionCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isZero();
        assertThat(result.predicted()).isZero();
        assertThat(result.notApplicable()).isZero();
        assertThat(result.failed()).isZero();
        verifyNoInteractions(predictionService);
    }

    @Test
    void aSuccessfulPredictionReachesTheExistingPredictionServiceForEachConfiguredTrain() {
        PredictionService predictionService = mock(PredictionService.class);
        PredictionCollectionService service =
                new PredictionCollectionService(predictionService, trainList("12952", "12002"));

        PredictionCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isEqualTo(2);
        assertThat(result.predicted()).isEqualTo(2);
        assertThat(result.notApplicable()).isZero();
        assertThat(result.failed()).isZero();
        verify(predictionService, times(1)).getPrediction(eq("12952"));
        verify(predictionService, times(1)).getPrediction(eq("12002"));
    }

    @Test
    void aTrainWithNoNextStationIsCountedSeparatelyFromAFailureAndCreatesNoSnapshot() {
        // PredictionNotApplicableException is thrown by PredictionService.getPrediction itself,
        // before PredictionSnapshotRecorder.recordSafely ever runs - so no snapshot is created for
        // this train, exactly like a real 422 API response never creates one.
        PredictionService predictionService = mock(PredictionService.class);
        when(predictionService.getPrediction("22415")).thenThrow(new PredictionNotApplicableException("22415"));
        PredictionCollectionService service = new PredictionCollectionService(predictionService, trainList("22415"));

        PredictionCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isEqualTo(1);
        assertThat(result.predicted()).isZero();
        assertThat(result.notApplicable()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void oneTrainFailureDoesNotPreventTheOthersFromBeingAttempted() {
        PredictionService predictionService = mock(PredictionService.class);
        when(predictionService.getPrediction("12952")).thenThrow(new RailRadarUnavailableException("down"));
        when(predictionService.getPrediction("22415")).thenThrow(new PredictionNotApplicableException("22415"));
        when(predictionService.getPrediction("12919")).thenReturn(null);
        PredictionCollectionService service =
                new PredictionCollectionService(predictionService, trainList("12952", "22415", "12919"));

        PredictionCollectionResult result = service.collectAll();

        assertThat(result.trainsAttempted()).isEqualTo(3);
        assertThat(result.predicted()).isEqualTo(1);
        assertThat(result.notApplicable()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(predictionService).getPrediction("12952");
        verify(predictionService).getPrediction("22415");
        verify(predictionService).getPrediction("12919");
    }

    @Test
    void neverThrowsEvenWhenEveryConfiguredTrainFails() {
        PredictionService predictionService = mock(PredictionService.class);
        when(predictionService.getPrediction("12952")).thenThrow(new RailRadarUnavailableException("down"));
        PredictionCollectionService service =
                new PredictionCollectionService(predictionService, trainList("12952"));

        PredictionCollectionResult result = service.collectAll();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.predicted()).isZero();
    }
}
