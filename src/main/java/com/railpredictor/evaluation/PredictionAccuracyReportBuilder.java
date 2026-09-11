package com.railpredictor.evaluation;

import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyComparison;
import com.railpredictor.model.domain.PredictionAccuracyMetrics;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracySlice;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link PredictionAccuracyComparison} (current model vs. a simple baseline) from a set
 * of evaluated {@link PredictionSnapshot}s (Phase 16H-5) - pure, no persistence; the caller is
 * responsible for fetching the snapshots (e.g. via {@code PredictionSnapshotRepository} once a
 * future phase needs an on-demand or scheduled report).
 *
 * <p><b>Baseline definition</b>: "current-delay-only" - the error a naive model would have made
 * by assuming the train's delay never changes between the prediction moment and the next station
 * ({@code currentDelayMinutes - actualDelayMinutes}), requiring no separate prediction call. A
 * future model variant (e.g. once weather is added) can be compared the same way, by computing its
 * own errors and calling {@link PredictionAccuracyCalculator#compute} again - this builder's shape
 * does not need to change for that.
 *
 * <p>Only {@link PredictionEvaluationStatus#EVALUATED_EXACT}/{@code EVALUATED_APPROXIMATE}
 * snapshots contribute - {@code PENDING} (no outcome yet) and {@code NOT_EVALUABLE} snapshots are
 * silently excluded, never treated as zero-error.
 */
@Component
public class PredictionAccuracyReportBuilder {

    private final PredictionAccuracyCalculator calculator;

    public PredictionAccuracyReportBuilder(PredictionAccuracyCalculator calculator) {
        this.calculator = calculator;
    }

    public PredictionAccuracyComparison build(List<PredictionSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");

        List<PredictionSnapshot> evaluated = snapshots.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT
                        || s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_APPROXIMATE)
                .filter(s -> !s.quarantined())
                .toList();

        List<Integer> currentModelErrors = evaluated.stream().map(PredictionSnapshot::errorMinutes).toList();
        List<Integer> baselineErrors = evaluated.stream()
                .map(s -> s.currentDelayMinutes() - s.actualDelayMinutes())
                .toList();

        PredictionAccuracyMetrics currentModel = calculator.compute(currentModelErrors);
        PredictionAccuracyMetrics baseline = calculator.compute(baselineErrors);
        return new PredictionAccuracyComparison(currentModel, baseline);
    }

    /**
     * Builds the richer Phase 16H-6 {@link PredictionAccuracyReport} - "overall" (exact +
     * approximate, exact/approximate counts exposed), "exact only" (for direct comparison against
     * "overall", per this phase's exact/approximate-uncertainty requirement), and a breakdown by
     * {@link HistoricalAdjustmentSource}, by provenance string, and (Phase 16H-7) by
     * {@link PredictionEvaluationMode}. Every slice reuses {@link #build} so the current-model/
     * baseline math is computed identically everywhere - this method only decides which snapshots
     * go into which slice, never how a slice's own metrics are computed.
     *
     * <p>{@code snapshots} is expected to already be whatever caller-side filter (train/station/
     * time range/etc., see {@code PredictionAccuracyReportService}) should apply - this method only
     * ever narrows further, by evaluation-exactness/source/provenance, never re-applies or ignores
     * an caller's own filter.
     */
    public PredictionAccuracyReport buildReport(List<PredictionSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");

        List<PredictionSnapshot> evaluated = snapshots.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT
                        || s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_APPROXIMATE)
                .filter(s -> !s.quarantined())
                .toList();
        List<PredictionSnapshot> exactOnlySnapshots = evaluated.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT)
                .toList();

        PredictionAccuracySlice overall = sliceOf(evaluated);
        PredictionAccuracyComparison exactOnly = build(exactOnlySnapshots);

        Map<HistoricalAdjustmentSource, PredictionAccuracySlice> bySource = new EnumMap<>(HistoricalAdjustmentSource.class);
        for (HistoricalAdjustmentSource source : HistoricalAdjustmentSource.values()) {
            bySource.put(source, sliceOf(evaluated.stream()
                    .filter(s -> s.historicalAdjustmentSource() == source)
                    .toList()));
        }

        Map<String, PredictionAccuracySlice> byProvenance = new LinkedHashMap<>();
        for (String provenance : evaluated.stream()
                .map(PredictionSnapshot::historicalAdjustmentProvenance)
                .distinct()
                .sorted()
                .toList()) {
            byProvenance.put(provenance, sliceOf(evaluated.stream()
                    .filter(s -> s.historicalAdjustmentProvenance().equals(provenance))
                    .toList()));
        }

        Map<PredictionEvaluationMode, PredictionAccuracySlice> byEvaluationMode = new EnumMap<>(PredictionEvaluationMode.class);
        for (PredictionEvaluationMode mode : PredictionEvaluationMode.values()) {
            byEvaluationMode.put(mode, sliceOf(evaluated.stream()
                    .filter(s -> s.evaluationMode() == mode)
                    .toList()));
        }

        return new PredictionAccuracyReport(overall, exactOnly, bySource, byProvenance, byEvaluationMode);
    }

    private PredictionAccuracySlice sliceOf(List<PredictionSnapshot> evaluatedSlice) {
        int exactCount = (int) evaluatedSlice.stream()
                .filter(s -> s.evaluationStatus() == PredictionEvaluationStatus.EVALUATED_EXACT)
                .count();
        int approximateCount = evaluatedSlice.size() - exactCount;
        PredictionAccuracyComparison comparison = build(evaluatedSlice);
        return new PredictionAccuracySlice(evaluatedSlice.size(), exactCount, approximateCount, comparison, improvementPercent(comparison));
    }

    /** {@code null}, not {@code 0.0}, when the baseline's own MAE is zero - see
     * {@link PredictionAccuracySlice#improvementPercent()}. */
    private static Double improvementPercent(PredictionAccuracyComparison comparison) {
        double baselineMae = comparison.baseline().meanAbsoluteError();
        if (baselineMae == 0.0) {
            return null;
        }
        return (baselineMae - comparison.currentModel().meanAbsoluteError()) / baselineMae * 100.0;
    }
}
