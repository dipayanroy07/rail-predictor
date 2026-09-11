package com.railpredictor.evaluation;

import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The one thing in this codebase that actually calls {@link PredictionEvaluationRefresher} - on a
 * fixed schedule, independent of any live prediction request (Phase 16H-5, mirroring
 * {@code HistoricalDelayProfileRefreshScheduler}'s established pattern exactly). This is what
 * keeps expensive-ish evaluation work (querying historical observations for every pending
 * snapshot) out of the live REST request path entirely.
 *
 * <p>A no-op whenever evaluation is disabled ({@code prediction.evaluation.enabled=false}, the
 * default) or no snapshot database is configured - checked directly (not by calling the refresher
 * and catching its {@link IllegalStateException}), so the common case is a quiet no-op rather than
 * a logged failure on every tick.
 */
@Component
public class PredictionEvaluationRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PredictionEvaluationRefreshScheduler.class);

    private final Optional<PredictionSnapshotRepository> snapshotRepository;
    private final PredictionEvaluationProperties properties;
    private final PredictionEvaluationRefresher refresher;

    public PredictionEvaluationRefreshScheduler(
            Optional<PredictionSnapshotRepository> snapshotRepository,
            PredictionEvaluationProperties properties,
            PredictionEvaluationRefresher refresher) {
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.refresher = refresher;
    }

    @Scheduled(
            fixedDelayString = "${prediction.evaluation.refresh-interval-ms:3600000}",
            initialDelayString = "${prediction.evaluation.refresh-initial-delay-ms:120000}")
    public void refreshPendingEvaluations() {
        if (!properties.enabled() || snapshotRepository.isEmpty()) {
            log.debug("Prediction evaluation is disabled or no snapshot database is configured - skipping");
            return;
        }
        try {
            int evaluated = refresher.evaluatePendingSnapshots();
            log.debug("Evaluated {} previously-pending prediction snapshot(s)", evaluated);
        } catch (RuntimeException e) {
            log.warn("Failed to evaluate pending prediction snapshots: {}", e.getMessage());
        }
    }
}
