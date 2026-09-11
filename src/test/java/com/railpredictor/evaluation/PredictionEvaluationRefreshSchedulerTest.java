package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PredictionEvaluationRefreshSchedulerTest {

    private final PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
    private final PredictionEvaluationRefresher refresher = mock(PredictionEvaluationRefresher.class);

    @Test
    void doesNothingWhenEvaluationIsDisabled() {
        PredictionEvaluationRefreshScheduler scheduler = new PredictionEvaluationRefreshScheduler(
                Optional.of(repository), new PredictionEvaluationProperties(false), refresher);

        scheduler.refreshPendingEvaluations();

        verify(refresher, never()).evaluatePendingSnapshots();
    }

    @Test
    void doesNothingWhenNoRepositoryIsConfigured() {
        PredictionEvaluationRefreshScheduler scheduler = new PredictionEvaluationRefreshScheduler(
                Optional.empty(), new PredictionEvaluationProperties(true), refresher);

        scheduler.refreshPendingEvaluations();

        verify(refresher, never()).evaluatePendingSnapshots();
    }

    @Test
    void callsTheRefresherWhenEnabledAndConfigured() {
        when(refresher.evaluatePendingSnapshots()).thenReturn(2);
        PredictionEvaluationRefreshScheduler scheduler = new PredictionEvaluationRefreshScheduler(
                Optional.of(repository), new PredictionEvaluationProperties(true), refresher);

        scheduler.refreshPendingEvaluations();

        verify(refresher).evaluatePendingSnapshots();
    }

    @Test
    void aRefresherFailureIsCaughtRatherThanPropagated() {
        when(refresher.evaluatePendingSnapshots()).thenThrow(new RuntimeException("db hiccup"));
        PredictionEvaluationRefreshScheduler scheduler = new PredictionEvaluationRefreshScheduler(
                Optional.of(repository), new PredictionEvaluationProperties(true), refresher);

        assertThatCode(scheduler::refreshPendingEvaluations).doesNotThrowAnyException();
    }
}
