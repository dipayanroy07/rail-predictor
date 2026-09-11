package com.railpredictor.evaluation;

import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.exception.InvalidAccuracyFilterException;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracyReportFilter;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.repository.PredictionSnapshotRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The read side of Phase 16H-6: turns a {@link PredictionAccuracyReportFilter} into a
 * {@link PredictionAccuracyReport}, entirely separate from the live prediction path and from the
 * offline {@code PredictionEvaluationRefresher} - this class only ever reads already-evaluated
 * snapshots, it never evaluates or records anything.
 *
 * <p>Loads snapshots via the existing {@code findByEvaluationStatusIn} query (no new repository
 * method was needed) and applies every other filter dimension in memory via
 * {@link PredictionAccuracyReportFilter#matches}. This is a deliberate choice for this phase: the
 * evaluation dataset is expected to be small (evaluation is disabled by default, and even once
 * enabled, one row is recorded per live prediction with a next station), so in-memory filtering
 * over a single indexed status query is clearly sufficient - see docs/prediction-model.md's Phase
 * 16H-6 notes for the full reasoning and the point at which this would need to change (e.g. adding
 * a JPQL query once train/station/date-range filtering needs to run inside the database).
 */
@Service
public class PredictionAccuracyReportService {

    private static final List<String> EVALUATED_STATUSES = List.of(
            PredictionEvaluationStatus.EVALUATED_EXACT.name(),
            PredictionEvaluationStatus.EVALUATED_APPROXIMATE.name());

    private final Optional<PredictionSnapshotRepository> repository;
    private final PredictionSnapshotEntityMapper entityMapper;
    private final PredictionAccuracyReportBuilder reportBuilder;
    private final PredictionEvaluationProperties properties;

    public PredictionAccuracyReportService(
            Optional<PredictionSnapshotRepository> repository,
            PredictionSnapshotEntityMapper entityMapper,
            PredictionAccuracyReportBuilder reportBuilder,
            PredictionEvaluationProperties properties) {
        this.repository = repository;
        this.entityMapper = entityMapper;
        this.reportBuilder = reportBuilder;
        this.properties = properties;
    }

    /**
     * @return {@code Optional.empty()} when prediction evaluation is disabled
     *         ({@code prediction.evaluation.enabled=false}, the default) - a deliberate, explicit
     *         "disabled" outcome, distinct from a report with zero evaluated samples (a valid
     *         result meaning "enabled, but nothing evaluated yet"). Never auto-enables evaluation.
     * @throws InvalidAccuracyFilterException if {@code filter} is unsatisfiable (e.g.
     *         {@code predictionMadeFrom} after {@code predictionMadeTo})
     */
    public Optional<PredictionAccuracyReport> getReport(PredictionAccuracyReportFilter filter) {
        Objects.requireNonNull(filter, "filter");
        if (!properties.enabled()) {
            return Optional.empty();
        }
        validate(filter);

        List<PredictionSnapshot> snapshots = repository
                .map(repo -> repo.findByEvaluationStatusIn(EVALUATED_STATUSES))
                .orElseGet(List::of)
                .stream()
                .map(entityMapper::toDomain)
                .filter(s -> !s.quarantined())
                .filter(filter::matches)
                .toList();

        return Optional.of(reportBuilder.buildReport(snapshots));
    }

    private static void validate(PredictionAccuracyReportFilter filter) {
        if (filter.predictionMadeFrom() != null && filter.predictionMadeTo() != null
                && filter.predictionMadeFrom().isAfter(filter.predictionMadeTo())) {
            throw new InvalidAccuracyFilterException(
                    "predictionMadeFrom (" + filter.predictionMadeFrom() + ") must not be after predictionMadeTo ("
                            + filter.predictionMadeTo() + ")");
        }
    }
}
