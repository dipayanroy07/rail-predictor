package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.config.HistoricalCollectionProperties;
import com.railpredictor.config.PredictionCollectionProperties;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PredictionCollectionSchedulerTest {

    private static PredictionCollectionProperties collectionProperties(boolean enabled) {
        return new PredictionCollectionProperties(enabled, 3600000, 600000);
    }

    private static HistoricalCollectionProperties trainList(String... trainNumbers) {
        return new HistoricalCollectionProperties(true, 3600000, 300000, List.of(trainNumbers));
    }

    @Test
    void doesNothingWhenDisabled() {
        PredictionCollectionService service = mock(PredictionCollectionService.class);
        PredictionCollectionScheduler scheduler =
                new PredictionCollectionScheduler(collectionProperties(false), trainList("12952"), service);

        scheduler.collect();

        verify(service, never()).collectAll();
    }

    @Test
    void doesNothingWhenEnabledButNoTrainNumbersAreConfigured() {
        PredictionCollectionService service = mock(PredictionCollectionService.class);
        PredictionCollectionScheduler scheduler =
                new PredictionCollectionScheduler(collectionProperties(true), trainList(), service);

        scheduler.collect();

        verify(service, never()).collectAll();
    }

    @Test
    void invokesTheCollectionServiceWhenEnabledAndConfigured() {
        PredictionCollectionService service = mock(PredictionCollectionService.class);
        when(service.collectAll()).thenReturn(new PredictionCollectionResult(1, 1, 0, 0));
        PredictionCollectionScheduler scheduler =
                new PredictionCollectionScheduler(collectionProperties(true), trainList("12952"), service);

        scheduler.collect();

        verify(service, times(1)).collectAll();
    }

    @Test
    void aCollectionFailureIsCaughtRatherThanPropagated() {
        PredictionCollectionService service = mock(PredictionCollectionService.class);
        when(service.collectAll()).thenThrow(new RuntimeException("db hiccup"));
        PredictionCollectionScheduler scheduler =
                new PredictionCollectionScheduler(collectionProperties(true), trainList("12952"), service);

        assertThatCode(scheduler::collect).doesNotThrowAnyException();
    }

    @Test
    void aFailedRunReleasesTheOverlapGuardSoTheNextTickCanStillRun() {
        PredictionCollectionService service = mock(PredictionCollectionService.class);
        when(service.collectAll()).thenThrow(new RuntimeException("db hiccup"));
        PredictionCollectionScheduler scheduler =
                new PredictionCollectionScheduler(collectionProperties(true), trainList("12952"), service);

        scheduler.collect();
        scheduler.collect();

        verify(service, times(2)).collectAll();
    }

    @Test
    void overlappingExecutionIsPrevented() throws InterruptedException {
        CountDownLatch collectAllEntered = new CountDownLatch(1);
        CountDownLatch releaseCollectAll = new CountDownLatch(1);
        PredictionCollectionService service = mock(PredictionCollectionService.class);
        when(service.collectAll()).thenAnswer(invocation -> {
            collectAllEntered.countDown();
            releaseCollectAll.await(5, TimeUnit.SECONDS);
            return new PredictionCollectionResult(1, 1, 0, 0);
        });
        PredictionCollectionScheduler scheduler =
                new PredictionCollectionScheduler(collectionProperties(true), trainList("12952"), service);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(scheduler::collect);
            assertThatCode(() -> collectAllEntered.await(5, TimeUnit.SECONDS)).doesNotThrowAnyException();

            // The first run is still blocked inside collectAll() - a second tick firing now must
            // be skipped, not run concurrently.
            scheduler.collect();

            releaseCollectAll.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(service, times(1)).collectAll();
    }
}
