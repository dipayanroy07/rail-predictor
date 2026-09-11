package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionOutcomeMatcherTest {

    private final PredictionOutcomeMatcher matcher = new PredictionOutcomeMatcher();

    private static final Instant PREDICTION_MADE_AT = Instant.parse("2026-09-09T10:00:00Z");

    private static PredictionSnapshot pendingSnapshot(String trainNumber, String targetStationCode) {
        return new PredictionSnapshot(
                1L, trainNumber, PREDICTION_MADE_AT, targetStationCode,
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null);
    }

    private static HistoricalObservation observation(
            String trainNumber, String stationCode, Integer arrivalDelay, Instant observedAt) {
        return observation(trainNumber, stationCode, arrivalDelay, observedAt, LocalDate.of(2026, 9, 9));
    }

    private static HistoricalObservation observation(
            String trainNumber, String stationCode, Integer arrivalDelay, Instant observedAt, LocalDate journeyDate) {
        return new HistoricalObservation(
                trainNumber, journeyDate, stationCode, 5,
                "10:00", "10:05", "10:07", "10:09",
                arrivalDelay, arrivalDelay, observedAt, DataProvenance.RAILRADAR);
    }

    @Test
    void exactTrainAndStationMatchAfterPredictionIsEvaluatedExact() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.actualDelayMinutes()).isEqualTo(6);
        assertThat(result.errorMinutes()).isEqualTo(2); // predicted 8 - actual 6
        assertThat(result.evaluatedAt()).isEqualTo(Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void wrongTrainNumberIsNeverMatched() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation otherTrain = observation("99999", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(otherTrain));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.PENDING);
    }

    @Test
    void wrongStationIsNeverMatched() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation otherStation = observation("12952", "RTM", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(otherStation));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.PENDING);
    }

    @Test
    void anObservationRecordedBeforeThePredictionCanNeverBeUsedAsItsOutcome() {
        // Critical leakage-prevention proof: this observation exists but predates the prediction.
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation stale = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T09:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(stale));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.PENDING);
        assertThat(result.actualDelayMinutes()).isNull();
    }

    @Test
    void anObservationRecordedAtExactlyThePredictionInstantIsAlsoExcluded() {
        // Conservative boundary: "strictly after," not "at or after" - see the matcher's own Javadoc.
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation sameInstant = observation("12952", "KOTA", 6, PREDICTION_MADE_AT);

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(sameInstant));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.PENDING);
    }

    @Test
    void anObservationAfterThePredictionIsUsedAsTheOutcome() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation future = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T10:00:01Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(future));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
    }

    @Test
    void multipleFutureCandidatesAreMarkedApproximateUsingTheEarliestOne() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation earlier = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));
        HistoricalObservation later = observation("12952", "KOTA", 20, Instant.parse("2026-09-10T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(later, earlier));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_APPROXIMATE);
        assertThat(result.actualDelayMinutes()).isEqualTo(6);
        assertThat(result.evaluatedAt()).isEqualTo(Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void missingOutcomeLeavesTheSnapshotPending() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of());

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.PENDING);
        assertThat(result).isEqualTo(snapshot);
    }

    @Test
    void anObservationWithNoArrivalDelayIsNeverUsedAsAnOutcome() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation noDelay = observation("12952", "KOTA", null, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(noDelay));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.PENDING);
    }

    @Test
    void anAlreadyEvaluatedSnapshotIsNeverReEvaluated() {
        PredictionSnapshot alreadyEvaluated = new PredictionSnapshot(
                1L, "12952", PREDICTION_MADE_AT, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
        HistoricalObservation differentOutcome = observation("12952", "KOTA", 99, Instant.parse("2026-09-09T13:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(alreadyEvaluated, List.of(differentOutcome));

        assertThat(result).isEqualTo(alreadyEvaluated);
    }

    @Test
    void duplicateIdenticalObservationsStillProduceExactWhenTheyRepresentTheSameRow() {
        // Two identical rows (e.g. re-fetched) for the same instant should not be treated as two
        // distinct ambiguous journeys.
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation observation = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(observation, observation));

        // Two entries at the identical observedAt still count as more than one candidate under
        // this matcher's conservative rule - marked APPROXIMATE rather than assuming they're the
        // same fact, since the matcher has no way to know they're duplicates rather than a
        // same-instant coincidence.
        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_APPROXIMATE);
        assertThat(result.actualDelayMinutes()).isEqualTo(6);
    }

    @Test
    void evaluationModeIsCarriedThroughUnchangedNeverRecomputedOrReset() {
        // Phase 16H-7: the snapshot is the audit record of which process made the prediction -
        // evaluating its outcome must never silently reset that back to a default.
        PredictionSnapshot snapshot = new PredictionSnapshot(
                1L, "12952", PREDICTION_MADE_AT, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                com.railpredictor.model.domain.PredictionEvaluationMode.HISTORICAL_BACKTEST);
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.evaluationMode())
                .isEqualTo(com.railpredictor.model.domain.PredictionEvaluationMode.HISTORICAL_BACKTEST);
    }

    @Test
    void weatherProvenanceIsCarriedThroughUnchangedNeverRecomputedOrReset() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                1L, "12952", PREDICTION_MADE_AT, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION,
                DataProvenance.OPENMETEO);
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.weatherProvenance()).isEqualTo(DataProvenance.OPENMETEO);
    }

    @Test
    void disruptionImpactMinutesIsCarriedThroughUnchangedNeverRecomputedOrReset() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                1L, "12952", PREDICTION_MADE_AT, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION,
                DataProvenance.MOCK, 20);
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.disruptionImpactMinutes()).isEqualTo(20);
    }

    @Test
    void phase21NextStationFieldsAreCarriedThroughUnchangedNeverResetOrMisderived() {
        // Regression test for a real Phase 22 audit finding: this matcher previously reconstructed
        // the evaluated snapshot via the pre-Phase-21 constructor, which reset
        // nextStationHistoricalAdjustmentMinutes/Source/Provenance to 0/NONE/UNAVAILABLE and
        // mis-derived predictedExtraDelayMinutes as predictedNextStationDelayMinutes -
        // currentDelayMinutes (double-counting the next-station historical/disruption
        // contribution into "simulation") - corrupting the Phase 21 audit trail the instant a
        // snapshot was evaluated.
        PredictionSnapshot snapshot = new PredictionSnapshot(
                1L, "12952", PREDICTION_MADE_AT, "KOTA",
                5, 20, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION,
                DataProvenance.OPENMETEO, 5,
                7, HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR, 8);
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.nextStationHistoricalAdjustmentMinutes()).isEqualTo(7);
        assertThat(result.nextStationHistoricalAdjustmentSource()).isEqualTo(HistoricalAdjustmentSource.SECTION);
        assertThat(result.nextStationHistoricalAdjustmentProvenance()).isEqualTo(DataProvenance.RAILRADAR);
        assertThat(result.predictedExtraDelayMinutes()).isEqualTo(8);
    }

    @Test
    void quarantineStateIsCarriedThroughUnchangedNeverAppliedOrClearedByEvaluation() {
        // Phase 22E: evaluating an outcome must never itself quarantine (or un-quarantine) a
        // snapshot - that is a separate, explicit, evidence-based action (see
        // PredictionSnapshotQuarantineService / migration V10), never inferred here.
        PredictionSnapshot snapshot = new PredictionSnapshot(
                1L, "12952", PREDICTION_MADE_AT, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.PENDING, null, null, null,
                com.railpredictor.model.domain.PredictionEvaluationMode.LIVE_EVALUATION, null, null,
                0, HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE, 3,
                true, "matched against a RailRadar upcoming-stop placeholder (Phase 22D/22C)");
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.quarantined()).isTrue();
        assertThat(result.quarantineReason())
                .isEqualTo("matched against a RailRadar upcoming-stop placeholder (Phase 22D/22C)");
    }

    @Test
    void nonQuarantinedSnapshotsRemainNonQuarantinedAfterEvaluation() {
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation match = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(match));

        assertThat(result.quarantined()).isFalse();
        assertThat(result.quarantineReason()).isNull();
    }

    // --- Phase 22D: documented residual limitation - no reliable journey identity exists ---

    @Test
    void observedAtProvesOnlyWhenRecordedNeverWhenTheRailwayEventOccurred() {
        // Characterization test, not a new guarantee: this matcher's only defense is a real
        // timestamp comparison (observedAt > predictionMadeAt) - it has no way to know whether the
        // underlying railway event genuinely happened after predictionMadeAt, only that this
        // application recorded/persisted the observation after that instant. A HistoricalObservation
        // instance existing at all is (since Phase 22D) already RailRadar-verified as a genuine,
        // already-occurred event by HistoricalObservationMapper's own status-based eligibility
        // check - but that verification happens upstream of this class, not here. This test exists
        // so a future reader never assumes this class independently re-verifies event authenticity.
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation observation = observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(observation));

        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_EXACT);
        assertThat(result.evaluatedAt()).isEqualTo(observation.observedAt());
    }

    @Test
    void sameTrainAndStationOnDifferentJourneyDatesAreNotDisambiguatedByJourneyIdentity() {
        // Documents a known, accepted limitation (docs/historical-data-design.md's Phase 22D
        // section): no RailRadar field reliably identifies a physical journey/run, so two
        // observations for the same (train, station) on different journeyDates are disambiguated
        // purely by observedAt ordering - exactly like same-day repeats - never by journey
        // identity, because none exists. This is NOT a bug to fix here; it is why ambiguous
        // matches are marked EVALUATED_APPROXIMATE rather than EVALUATED_EXACT.
        PredictionSnapshot snapshot = pendingSnapshot("12952", "KOTA");
        HistoricalObservation todayJourney =
                observation("12952", "KOTA", 6, Instant.parse("2026-09-09T11:00:00Z"), LocalDate.of(2026, 9, 9));
        HistoricalObservation differentJourneyDate =
                observation("12952", "KOTA", 99, Instant.parse("2026-09-09T12:00:00Z"), LocalDate.of(2026, 9, 8));

        PredictionSnapshot result = matcher.evaluate(snapshot, List.of(todayJourney, differentJourneyDate));

        // Both are accepted as candidates regardless of journeyDate - ambiguity is correctly
        // surfaced (APPROXIMATE), but resolved purely by observedAt ordering, not journey identity.
        assertThat(result.evaluationStatus()).isEqualTo(PredictionEvaluationStatus.EVALUATED_APPROXIMATE);
        assertThat(result.actualDelayMinutes()).isEqualTo(6); // the earlier-observedAt candidate wins
    }
}
