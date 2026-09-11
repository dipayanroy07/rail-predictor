package com.railpredictor.evaluation;

import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.model.domain.Station;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Captures a {@link PredictionSnapshot} from a freshly-computed {@link PredictionResult} and
 * persists it - the durable record a later, offline process needs to answer "did this prediction
 * turn out to be accurate" (Phase 16H-5). Deliberately best-effort and optional, exactly like
 * {@code HistoricalObservationRecorder}: a failure here (or no database configured) must never
 * break live prediction.
 *
 * <p>Disabled by default ({@code prediction.evaluation.enabled=false}) - recording a snapshot on
 * every live request is a new side effect this codebase's own conventions require an explicit
 * opt-in for (see {@link PredictionEvaluationProperties}).
 *
 * <p>{@code targetStationCode} is the train's next station <em>at prediction time</em>
 * ({@code train.nextStation()}) - see {@link PredictionSnapshot}'s own Javadoc for why this,
 * not the destination, is the evaluation target. A train with no next station (already
 * arrived/terminated) has nothing to evaluate, so no snapshot is recorded at all in that case -
 * this is the ordinary, expected "nothing to do" case, not a failure.
 */
@Component
public class PredictionSnapshotRecorder {

    private static final Logger log = LoggerFactory.getLogger(PredictionSnapshotRecorder.class);

    private final Optional<PredictionSnapshotRepository> repository;
    private final PredictionSnapshotEntityMapper entityMapper;
    private final PredictionEvaluationProperties properties;
    private final Clock clock;

    public PredictionSnapshotRecorder(
            Optional<PredictionSnapshotRepository> repository,
            PredictionSnapshotEntityMapper entityMapper,
            PredictionEvaluationProperties properties,
            Clock clock) {
        this.repository = repository;
        this.entityMapper = entityMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Records a snapshot for {@code result}, or does nothing when evaluation is disabled, no
     * database is configured, the train has no next station to evaluate against, or persistence
     * fails - never throws, mirroring every other best-effort recorder in this codebase. */
    public void recordSafely(PredictionResult result) {
        if (!properties.enabled() || repository.isEmpty()) {
            return;
        }
        Station nextStation = result.nextStation();
        if (nextStation == null) {
            return;
        }
        try {
            PredictionSnapshot snapshot = new PredictionSnapshot(
                    null,
                    result.trainNumber(),
                    Instant.now(clock),
                    nextStation.code(),
                    result.currentDelayMinutes(),
                    result.currentDelayMinutes() + result.predictedExtraDelayMinutes(),
                    result.predictedTotalDelayMinutes(),
                    result.predictedEta(),
                    result.historicalAdjustmentMinutes(),
                    result.historicalAdjustmentResolution().source(),
                    result.historicalAdjustmentResolution().provenance(),
                    result.confidence().score(),
                    PredictionEvaluationStatus.PENDING,
                    null, null, null,
                    PredictionEvaluationMode.LIVE_EVALUATION,
                    result.weatherProvenance(),
                    result.disruptionImpactAssessment().additionalDelayMinutes());
            repository.get().save(entityMapper.toEntity(snapshot));
        } catch (RuntimeException e) {
            log.warn("Could not record prediction snapshot for train {}: {}", result.trainNumber(), e.getMessage());
        }
    }
}
