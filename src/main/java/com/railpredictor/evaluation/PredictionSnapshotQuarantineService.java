package com.railpredictor.evaluation;

import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks one already-persisted {@link PredictionSnapshotEntity} as known-untrustworthy evaluation
 * evidence (Phase 22E) - reversible and non-destructive: only the {@code quarantined}/
 * {@code quarantine_reason} columns are touched, never {@code actual_delay_minutes}/
 * {@code error_minutes}/{@code evaluated_at} or any predicted value. Every accuracy/calibration
 * consumer (see {@code PredictionAccuracyReportBuilder}, {@code PredictionAccuracyReportService},
 * {@code CalibrationEvaluationReportBuilder}, {@code DataQualityAssessor}) excludes quarantined
 * rows from valid-evidence counts while still reporting their existence for audit.
 *
 * <p>This is a deliberately manual, explicit action - a snapshot is never quarantined
 * automatically or heuristically (see docs/historical-data-design.md's Phase 22E notes on why an
 * automatic rule would either be too broad or impossible to derive safely from what is currently
 * persisted). The Phase 22C incident's two known-contaminated snapshots were quarantined via a
 * one-time data migration (V10), not by calling this service at runtime - this service exists so a
 * future, similarly evidence-based correction does not need its own migration.
 */
@Component
public class PredictionSnapshotQuarantineService {

    private static final Logger log = LoggerFactory.getLogger(PredictionSnapshotQuarantineService.class);

    private final Optional<PredictionSnapshotRepository> repository;

    public PredictionSnapshotQuarantineService(Optional<PredictionSnapshotRepository> repository) {
        this.repository = repository;
    }

    /**
     * @throws IllegalStateException no snapshot database is configured
     * @throws java.util.NoSuchElementException no snapshot exists with {@code snapshotId}
     * @throws IllegalArgumentException {@code reason} is blank
     */
    @Transactional
    public void quarantine(Long snapshotId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank - a quarantine action must always be evidence-based");
        }
        PredictionSnapshotRepository repo = repository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot quarantine a prediction snapshot: no snapshot database is configured"));
        PredictionSnapshotEntity entity = repo.findById(snapshotId).orElseThrow(
                () -> new java.util.NoSuchElementException("No prediction snapshot exists with id " + snapshotId));
        entity.applyQuarantine(reason);
        repo.save(entity);
        log.info("Quarantined prediction snapshot {} (train {}): {}", snapshotId, entity.getTrainNumber(), reason);
    }
}
