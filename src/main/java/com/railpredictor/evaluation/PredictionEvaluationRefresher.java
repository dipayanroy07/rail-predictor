package com.railpredictor.evaluation;

import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges {@link PredictionOutcomeMatcher} (pure matching) to persistence: loads every
 * {@code PENDING} {@link PredictionSnapshot}, looks up candidate observations for its
 * (trainNumber, targetStationCode) pair, and persists the evaluation outcome if one was found
 * (Phase 16H-5). Not called from the live prediction path - see
 * {@code PredictionEvaluationRefreshScheduler}, the only caller, which runs this offline/on a
 * schedule, exactly like {@code HistoricalDelayProfileRefresher}'s own established pattern.
 *
 * <p>Reuses {@code HistoricalObservationRepository.findByTrainNumberAndStationCode} unchanged -
 * no new query was needed on that repository for this phase.
 */
@Component
public class PredictionEvaluationRefresher {

    private final Optional<PredictionSnapshotRepository> snapshotRepository;
    private final Optional<HistoricalObservationRepository> observationRepository;
    private final PredictionSnapshotEntityMapper snapshotEntityMapper;
    private final HistoricalObservationEntityMapper observationEntityMapper;
    private final PredictionOutcomeMatcher matcher;

    public PredictionEvaluationRefresher(
            Optional<PredictionSnapshotRepository> snapshotRepository,
            Optional<HistoricalObservationRepository> observationRepository,
            PredictionSnapshotEntityMapper snapshotEntityMapper,
            HistoricalObservationEntityMapper observationEntityMapper,
            PredictionOutcomeMatcher matcher) {
        this.snapshotRepository = snapshotRepository;
        this.observationRepository = observationRepository;
        this.snapshotEntityMapper = snapshotEntityMapper;
        this.observationEntityMapper = observationEntityMapper;
        this.matcher = matcher;
    }

    /**
     * @return how many pending snapshots were newly evaluated (still {@code PENDING} results are
     *         not counted)
     */
    @Transactional
    public int evaluatePendingSnapshots() {
        PredictionSnapshotRepository snapshots = snapshotRepository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot evaluate prediction snapshots: no snapshot database is configured "
                                + "(activate the \"postgres\" Spring profile - see docs/configuration.md)"));
        HistoricalObservationRepository observations = observationRepository.orElseThrow(
                () -> new IllegalStateException(
                        "Cannot evaluate prediction snapshots: no observation database is configured "
                                + "(activate the \"postgres\" Spring profile - see docs/configuration.md)"));

        List<PredictionSnapshotEntity> pending =
                snapshots.findByEvaluationStatus(PredictionEvaluationStatus.PENDING.name());

        int evaluatedCount = 0;
        for (PredictionSnapshotEntity entity : pending) {
            PredictionSnapshot before = snapshotEntityMapper.toDomain(entity);
            List<HistoricalObservation> candidates = observations
                    .findByTrainNumberAndStationCode(before.trainNumber(), before.targetStationCode())
                    .stream()
                    .map(observationEntityMapper::toDomain)
                    .toList();

            PredictionSnapshot after = matcher.evaluate(before, candidates);
            if (after.evaluationStatus() != PredictionEvaluationStatus.PENDING) {
                entity.applyEvaluation(
                        after.evaluationStatus().name(), after.actualDelayMinutes(), after.errorMinutes(), after.evaluatedAt());
                snapshots.save(entity);
                evaluatedCount++;
            }
        }
        return evaluatedCount;
    }
}
