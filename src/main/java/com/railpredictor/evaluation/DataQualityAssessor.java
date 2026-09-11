package com.railpredictor.evaluation;

import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.repository.PredictionSnapshotEntity;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Produces a real, honest {@link DataQualityReport} from whatever {@link PredictionSnapshot} rows
 * actually exist (Phase 20) - the first thing to check before trusting any accuracy number. Every
 * count is a genuine database count; nothing here is estimated, padded, or backed by mock/test
 * data.
 */
@Component
public class DataQualityAssessor {

    private static final String POINT_IN_TIME_NOTE =
            "Live evaluation snapshots reflect whatever was actually true at prediction time, by "
                    + "construction - they need no replay to be valid live-evaluation evidence. Replaying a "
                    + "snapshot's prediction under a different historical weight, however, is only "
                    + "point-in-time-reproducible for station/section historical profiles (both cutoff-aware, "
                    + "see docs/historical-data-design.md's Phase 16H-7/16F notes) - weather (Open-Meteo) and "
                    + "railway-disruption data have no historical archive at all, so no prediction can be "
                    + "counterfactually replayed with an alternate weather/disruption input; only its "
                    + "already-recorded weatherProvenance/disruptionImpactMinutes can be observed as-is.";

    private final Optional<PredictionSnapshotRepository> repository;
    private final PredictionSnapshotEntityMapper entityMapper;

    public DataQualityAssessor(Optional<PredictionSnapshotRepository> repository, PredictionSnapshotEntityMapper entityMapper) {
        this.repository = repository;
        this.entityMapper = entityMapper;
    }

    public DataQualityReport assess() {
        if (repository.isEmpty()) {
            return DataQualityReport.empty("No snapshot database is configured - activate the \"postgres\" "
                    + "Spring profile and enable prediction.evaluation to accumulate real evaluation data.");
        }

        List<PredictionSnapshotEntity> entities = repository.get().findAll();
        if (entities.isEmpty()) {
            return DataQualityReport.empty("A snapshot database is configured, but zero snapshots have been "
                    + "recorded yet - enable prediction.evaluation.enabled and run live predictions to "
                    + "accumulate real evaluation data.");
        }

        List<PredictionSnapshot> snapshots = entities.stream().map(entityMapper::toDomain).toList();

        int exact = countByStatus(snapshots, PredictionEvaluationStatus.EVALUATED_EXACT);
        int approximate = countByStatus(snapshots, PredictionEvaluationStatus.EVALUATED_APPROXIMATE);
        int pending = countByStatus(snapshots, PredictionEvaluationStatus.PENDING);
        int notEvaluable = countByStatus(snapshots, PredictionEvaluationStatus.NOT_EVALUABLE);

        List<PredictionSnapshot> evaluated = snapshots.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT
                        || s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_APPROXIMATE)
                .toList();

        int weatherAvailable = (int) evaluated.stream().filter(s -> s.weatherProvenance() != null).count();
        int historicalAvailable = (int) evaluated.stream()
                .filter(s -> s.historicalAdjustmentSource() != HistoricalAdjustmentSource.NONE).count();
        int disruptionAvailable = (int) evaluated.stream().filter(s -> s.disruptionImpactMinutes() != null).count();
        int simulationContributed = (int) evaluated.stream()
                .filter(s -> (s.predictedNextStationDelayMinutes() - s.currentDelayMinutes()) > 0).count();

        int distinctTrains = (int) snapshots.stream().map(PredictionSnapshot::trainNumber).distinct().count();
        int distinctStations = (int) snapshots.stream().map(PredictionSnapshot::targetStationCode).distinct().count();

        var earliest = snapshots.stream().map(PredictionSnapshot::predictionMadeAt).min(java.time.Instant::compareTo).orElse(null);
        var latest = snapshots.stream().map(PredictionSnapshot::predictionMadeAt).max(java.time.Instant::compareTo).orElse(null);

        return new DataQualityReport(
                snapshots.size(), exact + approximate, exact, approximate, pending, notEvaluable,
                weatherAvailable, historicalAvailable, disruptionAvailable, simulationContributed,
                distinctTrains, distinctStations, earliest, latest, POINT_IN_TIME_NOTE);
    }

    private static int countByStatus(List<PredictionSnapshot> snapshots, PredictionEvaluationStatus status) {
        return (int) snapshots.stream().filter(s -> s.evaluationStatus() == status).count();
    }
}
