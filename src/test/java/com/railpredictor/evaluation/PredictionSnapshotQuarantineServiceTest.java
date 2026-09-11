package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PredictionSnapshotQuarantineServiceTest {

    private static final String REASON =
            "matched against a RailRadar upcoming-stop placeholder (Phase 22D/22C)";

    @Test
    void quarantinesAnExistingSnapshotAndPersistsIt() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotEntity entity = mock(PredictionSnapshotEntity.class);
        when(repository.findById(42L)).thenReturn(Optional.of(entity));
        PredictionSnapshotQuarantineService service =
                new PredictionSnapshotQuarantineService(Optional.of(repository));

        service.quarantine(42L, REASON);

        verify(entity).applyQuarantine(REASON);
        verify(repository).save(entity);
    }

    @Test
    void throwsWhenNoSnapshotExistsWithTheGivenId() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        when(repository.findById(42L)).thenReturn(Optional.empty());
        PredictionSnapshotQuarantineService service =
                new PredictionSnapshotQuarantineService(Optional.of(repository));

        assertThatThrownBy(() -> service.quarantine(42L, REASON))
                .isInstanceOf(NoSuchElementException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsABlankReasonBeforeTouchingTheRepository() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotQuarantineService service =
                new PredictionSnapshotQuarantineService(Optional.of(repository));

        assertThatThrownBy(() -> service.quarantine(42L, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsANullReason() {
        PredictionSnapshotRepository repository = mock(PredictionSnapshotRepository.class);
        PredictionSnapshotQuarantineService service =
                new PredictionSnapshotQuarantineService(Optional.of(repository));

        assertThatThrownBy(() -> service.quarantine(42L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNoSnapshotDatabaseIsConfigured() {
        PredictionSnapshotQuarantineService service =
                new PredictionSnapshotQuarantineService(Optional.empty());

        assertThatThrownBy(() -> service.quarantine(42L, REASON))
                .isInstanceOf(IllegalStateException.class);
    }
}
