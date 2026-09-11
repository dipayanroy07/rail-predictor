package com.railpredictor.evaluation;

import com.railpredictor.model.domain.DataQualityReport;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.repository.HistoricalObservationRepository;
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
 *
 * <p>Phase 22 additionally counts the entirely separate {@code historical_observations} table, so
 * "no snapshot database configured" (state A), "database configured, genuinely nothing recorded
 * anywhere" (state B), and "real observations are being collected but no prediction snapshots
 * exist yet" (state C - e.g. live requests aren't being made, or {@code
 * prediction.evaluation.enabled=false}) are never collapsed into the same report.
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
    private final Optional<HistoricalObservationRepository> observationRepository;
    private final PredictionSnapshotEntityMapper entityMapper;

    public DataQualityAssessor(
            Optional<PredictionSnapshotRepository> repository,
            Optional<HistoricalObservationRepository> observationRepository,
            PredictionSnapshotEntityMapper entityMapper) {
        this.repository = repository;
        this.observationRepository = observationRepository;
        this.entityMapper = entityMapper;
    }

    public DataQualityReport assess() {
        if (repository.isEmpty()) {
            return DataQualityReport.empty("No snapshot database is configured - activate the \"postgres\" "
                    + "Spring profile and enable prediction.evaluation to accumulate real evaluation data.");
        }

        long observationCount = observationRepository.map(HistoricalObservationRepository::count).orElse(0L);

        List<PredictionSnapshotEntity> entities = repository.get().findAll();
        if (entities.isEmpty()) {
            String note = observationCount > 0
                    ? "A snapshot database is configured, and " + observationCount + " real historical "
                            + "observation(s) have been collected, but zero prediction snapshots exist yet - "
                            + "enable prediction.evaluation.enabled and make live prediction requests to start "
                            + "accumulating evaluation snapshots (observation collection and snapshot recording "
                            + "are independent: observations are recorded from every live RailRadar response "
                            + "regardless of prediction.evaluation.enabled, but snapshots require it explicitly)."
                    : "A snapshot database is configured, but zero snapshots (and zero historical observations) "
                            + "have been recorded yet - enable prediction.evaluation.enabled and run live "
                            + "predictions to accumulate real evaluation data.";
            return DataQualityReport.empty(note, (int) observationCount);
        }

        List<PredictionSnapshot> snapshots = entities.stream().map(entityMapper::toDomain).toList();

        int quarantined = (int) snapshots.stream().filter(PredictionSnapshot::quarantined).count();

        // Phase 22E: pending/notEvaluable are exhaustive-by-status regardless of quarantine (a
        // quarantined snapshot's evaluationStatus is untouched), but evaluated/exact/approximate
        // must only ever count valid (non-quarantined) evidence - computed below from `evaluated`.
        int pending = countByStatus(snapshots, PredictionEvaluationStatus.PENDING);
        int notEvaluable = countByStatus(snapshots, PredictionEvaluationStatus.NOT_EVALUABLE);

        List<PredictionSnapshot> evaluated = snapshots.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT
                        || s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_APPROXIMATE)
                .filter(s -> !s.quarantined())
                .toList();

        int validExact = (int) evaluated.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT).count();
        int validApproximate = evaluated.size() - validExact;

        int weatherAvailable = (int) evaluated.stream().filter(s -> s.weatherProvenance() != null).count();
        // Phase 21: historicalAvailable now reflects nextStationHistoricalAdjustmentSource - the
        // field that genuinely enters predictedNextStationDelayMinutes - never the destination-
        // scoped historicalAdjustmentSource, which does not.
        int historicalAvailable = (int) evaluated.stream()
                .filter(s -> s.nextStationHistoricalAdjustmentSource() != HistoricalAdjustmentSource.NONE).count();
        int disruptionAvailable = (int) evaluated.stream().filter(s -> s.disruptionImpactMinutes() != null).count();
        // Phase 21: predictedExtraDelayMinutes is simulation's own persisted raw contribution -
        // never predictedNextStationDelayMinutes - currentDelayMinutes, which (since this phase)
        // also includes the next-station historical adjustment and disruption impact and would
        // misattribute their contribution to simulation.
        int simulationContributed = (int) evaluated.stream().filter(s -> s.predictedExtraDelayMinutes() > 0).count();

        int distinctTrains = (int) snapshots.stream().map(PredictionSnapshot::trainNumber).distinct().count();
        int distinctStations = (int) snapshots.stream().map(PredictionSnapshot::targetStationCode).distinct().count();

        var earliest = snapshots.stream().map(PredictionSnapshot::predictionMadeAt).min(java.time.Instant::compareTo).orElse(null);
        var latest = snapshots.stream().map(PredictionSnapshot::predictionMadeAt).max(java.time.Instant::compareTo).orElse(null);

        return new DataQualityReport(
                snapshots.size(), validExact + validApproximate, validExact, validApproximate, pending, notEvaluable,
                weatherAvailable, historicalAvailable, disruptionAvailable, simulationContributed,
                distinctTrains, distinctStations, earliest, latest, POINT_IN_TIME_NOTE, (int) observationCount,
                quarantined);
    }

    private static int countByStatus(List<PredictionSnapshot> snapshots, PredictionEvaluationStatus status) {
        return (int) snapshots.stream().filter(s -> s.evaluationStatus() == status).count();
    }
}
