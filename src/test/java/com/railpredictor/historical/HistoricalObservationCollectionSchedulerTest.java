package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.config.HistoricalCollectionProperties;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HistoricalObservationCollectionSchedulerTest {

    private static HistoricalCollectionProperties properties(boolean enabled, String... trainNumbers) {
        return new HistoricalCollectionProperties(enabled, 3600000, 300000, List.of(trainNumbers));
    }

    @Test
    void doesNothingWhenDisabled() {
        HistoricalObservationCollectionService service = mock(HistoricalObservationCollectionService.class);
        HistoricalObservationCollectionScheduler scheduler =
                new HistoricalObservationCollectionScheduler(properties(false, "12952"), service);

        scheduler.collect();

        verify(service, never()).collectAll();
    }

    @Test
    void doesNothingWhenEnabledButNoTrainNumbersAreConfigured() {
        HistoricalObservationCollectionService service = mock(HistoricalObservationCollectionService.class);
        HistoricalObservationCollectionScheduler scheduler =
                new HistoricalObservationCollectionScheduler(properties(true), service);

        scheduler.collect();

        verify(service, never()).collectAll();
    }

    @Test
    void invokesTheCollectionServiceWhenEnabledAndConfigured() {
        HistoricalObservationCollectionService service = mock(HistoricalObservationCollectionService.class);
        when(service.collectAll()).thenReturn(new HistoricalObservationCollectionResult(1, 1, 0));
        HistoricalObservationCollectionScheduler scheduler =
                new HistoricalObservationCollectionScheduler(properties(true, "12952"), service);

        scheduler.collect();

        verify(service, times(1)).collectAll();
    }

    @Test
    void aCollectionFailureIsCaughtRatherThanPropagated() {
        HistoricalObservationCollectionService service = mock(HistoricalObservationCollectionService.class);
        when(service.collectAll()).thenThrow(new RuntimeException("db hiccup"));
        HistoricalObservationCollectionScheduler scheduler =
                new HistoricalObservationCollectionScheduler(properties(true, "12952"), service);

        assertThatCode(scheduler::collect).doesNotThrowAnyException();
    }

    @Test
    void aFailedRunReleasesTheOverlapGuardSoTheNextTickCanStillRun() {
        HistoricalObservationCollectionService service = mock(HistoricalObservationCollectionService.class);
        when(service.collectAll()).thenThrow(new RuntimeException("db hiccup"));
        HistoricalObservationCollectionScheduler scheduler =
                new HistoricalObservationCollectionScheduler(properties(true, "12952"), service);

        scheduler.collect();
        scheduler.collect();

        verify(service, times(2)).collectAll();
    }

    @Test
    void overlappingExecutionIsPrevented() throws InterruptedException {
        CountDownLatch collectAllEntered = new CountDownLatch(1);
        CountDownLatch releaseCollectAll = new CountDownLatch(1);
        HistoricalObservationCollectionService service = mock(HistoricalObservationCollectionService.class);
        when(service.collectAll()).thenAnswer(invocation -> {
            collectAllEntered.countDown();
            releaseCollectAll.await(5, TimeUnit.SECONDS);
            return new HistoricalObservationCollectionResult(1, 1, 0);
        });
        HistoricalObservationCollectionScheduler scheduler =
                new HistoricalObservationCollectionScheduler(properties(true, "12952"), service);

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
