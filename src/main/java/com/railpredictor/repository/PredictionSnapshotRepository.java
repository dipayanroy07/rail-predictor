package com.railpredictor.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link PredictionSnapshotEntity}. Only ever wired up when the "postgres"
 * profile is active - see application.properties and docs/configuration.md. Also gated behind
 * {@code prediction.evaluation.enabled} (default {@code false}) - see
 * {@code PredictionSnapshotRecorder}.
 */
public interface PredictionSnapshotRepository extends JpaRepository<PredictionSnapshotEntity, Long> {

    /** Every snapshot still awaiting a matching outcome - how
     * {@code PredictionEvaluationRefreshScheduler} discovers what to (re-)check. */
    List<PredictionSnapshotEntity> findByEvaluationStatus(String evaluationStatus);

    /** Every already-evaluated snapshot with a real, non-{@code PENDING}/{@code NOT_EVALUABLE}
     * outcome - the raw input to {@code PredictionAccuracyReportBuilder}. */
    List<PredictionSnapshotEntity> findByEvaluationStatusIn(List<String> evaluationStatuses);
}
