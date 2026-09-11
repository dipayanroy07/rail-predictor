package com.railpredictor.evaluation;

import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Pure matching logic: given one {@link PredictionSnapshot} and the candidate
 * {@link HistoricalObservation}s for its (trainNumber, targetStationCode) pair, decides whether -
 * and how confidently - it can now be evaluated (Phase 16H-5). No JPA, no repository access; see
 * {@code PredictionEvaluationRefresher} for where this is wired to persistence.
 *
 * <p><b>Leakage prevention (mandatory, non-negotiable):</b> an observation is only ever considered
 * a candidate future outcome when {@code observation.observedAt()} is <em>strictly after</em>
 * {@code snapshot.predictionMadeAt()} - {@code isAfter}, not {@code !isBefore}, so an observation
 * recorded at the exact same instant as the prediction is conservatively excluded too, never
 * treated as "the future." An observation that existed before (or at) the prediction was made can
 * never be used as its outcome, full stop - this is what makes evaluation honest rather than
 * circular.
 *
 * <p><b>Ambiguity handling:</b> zero matching candidates leaves the snapshot {@code PENDING} (the
 * train presumably hasn't reached the target station yet - re-checked later). Exactly one
 * candidate is an unambiguous match ({@code EVALUATED_EXACT}). More than one candidate means the
 * same (train, station) pair was recorded again after this prediction was made before it was ever
 * evaluated (e.g. the same service ran again) - which one truly corresponds to this prediction's
 * own journey cannot be known for certain (see docs/historical-data-design.md's Phase 16E notes on
 * why {@code journeyDate} is not a verified physical-journey identifier), so the <em>earliest</em>
 * candidate is used as the best available guess, but the result is marked
 * {@code EVALUATED_APPROXIMATE}, never silently treated as certain.
 *
 * <p><b>{@code evaluationMode}/{@code weatherProvenance}/{@code disruptionImpactMinutes} are
 * carried through unchanged (Phase 16H-7/17/19).</b> This method never decides or recomputes which
 * process originally produced the snapshot, or what weather/disruption data it was made with -
 * every one is copied verbatim onto the returned snapshot, exactly like
 * {@code historicalAdjustmentSource}/{@code historicalAdjustmentProvenance}. The snapshot is the
 * audit record of how (and with what data) the prediction was made; evaluating its outcome must
 * never alter that record.
 */
@Component
public class PredictionOutcomeMatcher {

    /**
     * @param candidateObservations every observation for the snapshot's own (trainNumber,
     *                              targetStationCode) pair - not pre-filtered; this method does
     *                              its own train-number/station/leakage filtering defensively
     */
    public PredictionSnapshot evaluate(PredictionSnapshot snapshot, List<HistoricalObservation> candidateObservations) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(candidateObservations, "candidateObservations");

        if (snapshot.evaluationStatus() != PredictionEvaluationStatus.PENDING) {
            // Already evaluated (or structurally not evaluable) - never re-evaluated/overwritten.
            return snapshot;
        }

        List<HistoricalObservation> futureCandidates = candidateObservations.stream()
                .filter(o -> o.trainNumber().equals(snapshot.trainNumber()))
                .filter(o -> o.stationCode().equals(snapshot.targetStationCode()))
                .filter(o -> o.arrivalDelayMinutes() != null)
                .filter(o -> o.observedAt().isAfter(snapshot.predictionMadeAt()))
                .sorted(Comparator.comparing(HistoricalObservation::observedAt))
                .toList();

        if (futureCandidates.isEmpty()) {
            return snapshot;
        }

        HistoricalObservation match = futureCandidates.get(0);
        PredictionEvaluationStatus status = futureCandidates.size() == 1
                ? PredictionEvaluationStatus.EVALUATED_EXACT
                : PredictionEvaluationStatus.EVALUATED_APPROXIMATE;
        int actualDelayMinutes = match.arrivalDelayMinutes();
        int errorMinutes = snapshot.predictedNextStationDelayMinutes() - actualDelayMinutes;

        return new PredictionSnapshot(
                snapshot.id(),
                snapshot.trainNumber(),
                snapshot.predictionMadeAt(),
                snapshot.targetStationCode(),
                snapshot.currentDelayMinutes(),
                snapshot.predictedNextStationDelayMinutes(),
                snapshot.predictedTotalDelayMinutes(),
                snapshot.predictedEta(),
                snapshot.historicalAdjustmentMinutes(),
                snapshot.historicalAdjustmentSource(),
                snapshot.historicalAdjustmentProvenance(),
                snapshot.confidenceScore(),
                status,
                actualDelayMinutes,
                errorMinutes,
                match.observedAt(),
                snapshot.evaluationMode(),
                snapshot.weatherProvenance(),
                snapshot.disruptionImpactMinutes());
    }
}
