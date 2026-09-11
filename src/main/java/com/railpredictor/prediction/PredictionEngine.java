package com.railpredictor.prediction;

import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.domain.DisruptionImpactStatus;
import com.railpredictor.model.domain.HistoricalAdjustmentResolution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.RemainingRouteHistoricalStatus;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import com.railpredictor.model.domain.RouteCompleteness;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import com.railpredictor.model.domain.SimulationResult;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.SectionType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Assembles the final {@link PredictionResult} from already-computed pieces (live train data, the
 * current route section, weather, an estimated section type, a simulation result, station-level
 * historical delay stats, and - Phase 16H-2 - a remaining-route section-level historical summary).
 * Does not itself fetch live data, run the simulation, look up history, or discover route
 * topology - wiring the full pipeline together is {@code PredictionService}'s job; this class only
 * decides how to combine numbers it's handed.
 *
 * <p>See docs/prediction-model.md for the formula this follows and why it avoids double-counting
 * current delay and recovery, and its "Phase 16H-2"/"Phase 16H-3" sections for exactly how the
 * single historical contribution is chosen between the section-level and station-level signals -
 * <b>never both</b> - and how that choice is now reported explicitly as a
 * {@link HistoricalAdjustmentResolution} rather than left implicit in warning text.
 */
@Component
public class PredictionEngine {

    private final TravelTimeCalculator travelTimeCalculator;
    private final DelayCalculator delayCalculator;
    private final HistoricalDelayCalculator historicalDelayCalculator;
    private final SectionHistoricalDelayCalculator sectionHistoricalDelayCalculator;
    private final ConfidenceCalculator confidenceCalculator;
    private final Clock clock;

    public PredictionEngine(
            TravelTimeCalculator travelTimeCalculator,
            DelayCalculator delayCalculator,
            HistoricalDelayCalculator historicalDelayCalculator,
            SectionHistoricalDelayCalculator sectionHistoricalDelayCalculator,
            ConfidenceCalculator confidenceCalculator,
            Clock clock) {
        this.travelTimeCalculator = travelTimeCalculator;
        this.delayCalculator = delayCalculator;
        this.historicalDelayCalculator = historicalDelayCalculator;
        this.sectionHistoricalDelayCalculator = sectionHistoricalDelayCalculator;
        this.confidenceCalculator = confidenceCalculator;
        this.clock = clock;
    }

    /**
     * Pre-Phase-16H-2 shape, preserved so existing callers need not change: no remaining-route
     * section historical summary is supplied, so the historical contribution is always the
     * station-level one - identical behaviour to before this phase.
     */
    public PredictionResult predict(
            LiveTrainData train,
            RouteSection section,
            WeatherData weather,
            SectionType sectionType,
            SimulationResult simulationResult,
            HistoricalDelay historicalDelay) {
        return predict(train, section, weather, sectionType, simulationResult, historicalDelay,
                unavailableSectionHistoricalSummary(train == null ? null : train.trainNumber()));
    }

    public PredictionResult predict(
            LiveTrainData train,
            RouteSection section,
            WeatherData weather,
            SectionType sectionType,
            SimulationResult simulationResult,
            HistoricalDelay historicalDelay,
            RemainingRouteHistoricalSummary sectionHistoricalSummary) {
        return predict(train, section, weather, sectionType, simulationResult, historicalDelay,
                sectionHistoricalSummary, DisruptionImpactAssessment.unavailable());
    }

    /**
     * Canonical shape (Phase 19): additionally takes the already-computed
     * {@link DisruptionImpactAssessment} for the current section - this method never queries
     * {@code RailwayDisruptionProvider} or evaluates a {@code DisruptionImpactPolicy} itself, only
     * combines an already-decided number, exactly like every other input here.
     */
    public PredictionResult predict(
            LiveTrainData train,
            RouteSection section,
            WeatherData weather,
            SectionType sectionType,
            SimulationResult simulationResult,
            HistoricalDelay historicalDelay,
            RemainingRouteHistoricalSummary sectionHistoricalSummary,
            DisruptionImpactAssessment disruptionImpactAssessment) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(sectionType, "sectionType");
        Objects.requireNonNull(simulationResult, "simulationResult");
        Objects.requireNonNull(historicalDelay, "historicalDelay");
        Objects.requireNonNull(sectionHistoricalSummary, "sectionHistoricalSummary");
        Objects.requireNonNull(disruptionImpactAssessment, "disruptionImpactAssessment");

        double baseTravelTimeMinutes = travelTimeCalculator.baseTravelTimeMinutes(train);
        int predictedExtraDelayMinutes = delayCalculator.predictedExtraDelayMinutes(simulationResult);

        // Exactly ONE historical contribution: section-level history REPLACES the station-level
        // adjustment when usable, it is never added alongside it - see docs/prediction-model.md.
        // The strategy actually selected is recorded explicitly (Phase 16H-3), not left for a
        // consumer to infer from warning text.
        HistoricalResolutionOutcome historicalOutcome = resolveHistoricalAdjustment(historicalDelay, sectionHistoricalSummary);
        int historicalAdjustmentMinutes = historicalOutcome.adjustmentMinutes();
        HistoricalAdjustmentResolution resolution = historicalOutcome.resolution();

        // Informational only: already netted into predictedExtraDelayMinutes above, so it must
        // not be subtracted again here - see docs/prediction-model.md.
        int recoveryMinutes = simulationResult.recoveredDelayMinutes();

        // Phase 19: a distinct fourth term, alongside current delay/historical adjustment - never
        // routed through predictedExtraDelayMinutes (simulated) or recoveryMinutes. Only non-null
        // for NO_KNOWN_DISRUPTION (0) and ESTIMATED (a concrete figure); DATA_UNAVAILABLE/
        // PRESENT_BUT_NOT_ESTIMABLE contribute nothing to the arithmetic here, on purpose - see
        // PredictionResult's own Javadoc and docs/prediction-model.md's Phase 19 notes.
        int disruptionImpactMinutes = disruptionImpactAssessment.additionalDelayMinutes() == null
                ? 0 : disruptionImpactAssessment.additionalDelayMinutes();

        int predictedTotalDelayMinutes = Math.max(0, train.currentDelayMinutes() + predictedExtraDelayMinutes
                + historicalAdjustmentMinutes + disruptionImpactMinutes);

        // Phase 21: a SEPARATE, next-station-scoped historical contribution - never
        // historicalAdjustmentMinutes above, which (when its source is SECTION) is the sum over
        // the ENTIRE remaining route and is therefore only valid for the destination-scoped
        // predictedTotalDelayMinutes/predictedEta computed above. See docs/prediction-model.md's
        // Phase 21 notes for the full reasoning. disruptionImpactMinutes, by contrast, is already
        // correctly next-section-scoped (DisruptionImpactAggregator is only ever given the current
        // section) and is reused as-is below - no new disruption term is needed.
        HistoricalResolutionOutcome nextStationHistoricalOutcome =
                resolveNextStationHistoricalAdjustment(historicalDelay, sectionHistoricalSummary);
        int nextStationHistoricalAdjustmentMinutes = nextStationHistoricalOutcome.adjustmentMinutes();
        HistoricalAdjustmentResolution nextStationHistoricalAdjustmentResolution = nextStationHistoricalOutcome.resolution();

        int predictedNextStationDelayMinutes = Math.max(0, train.currentDelayMinutes() + predictedExtraDelayMinutes
                + nextStationHistoricalAdjustmentMinutes + disruptionImpactMinutes);

        // Deliberately does NOT add currentDelayMinutes: "now" already reflects however delayed
        // the train currently is, so this is the time remaining from the current moment onward,
        // not a projection from the original scheduled departure. See docs/prediction-model.md.
        Instant predictedEta = Instant.now(clock)
                .plus(Duration.ofMinutes(Math.round(baseTravelTimeMinutes)))
                .plus(Duration.ofMinutes(predictedExtraDelayMinutes))
                .plus(Duration.ofMinutes(historicalAdjustmentMinutes))
                .plus(Duration.ofMinutes(disruptionImpactMinutes));

        ConfidenceScore confidence = confidenceCalculator.calculate(train, weather, resolution, section, simulationResult);

        List<String> warnings = new ArrayList<>();
        if (train.remainingDistanceKm() == null) {
            warnings.add("Remaining distance unknown - base travel time treated as 0 minutes.");
        }
        if (travelTimeCalculator.usedAssumedSpeed(train)) {
            warnings.add("Train speed unavailable or zero - base travel time used an assumed average speed.");
        }
        addHistoricalWarnings(warnings, resolution, sectionHistoricalSummary);
        addDisruptionImpactWarnings(warnings, disruptionImpactAssessment);
        warnings.addAll(confidence.warnings());

        return new PredictionResult(
                train.trainNumber(),
                train.trainName(),
                train.status(),
                train.currentStation(),
                train.nextStation(),
                train.currentDelayMinutes(),
                train.distanceFromOriginKm(),
                train.remainingDistanceKm(),
                train.speedKmh(),
                sectionType,
                baseTravelTimeMinutes,
                predictedExtraDelayMinutes,
                historicalAdjustmentMinutes,
                recoveryMinutes,
                predictedTotalDelayMinutes,
                predictedEta,
                confidence,
                simulationResult.disruptions(),
                simulationResult.cascadeEffects(),
                List.copyOf(warnings),
                resolution,
                weather == null ? null : weather.source(),
                disruptionImpactAssessment,
                nextStationHistoricalAdjustmentMinutes,
                nextStationHistoricalAdjustmentResolution,
                predictedNextStationDelayMinutes);
    }

    /**
     * The replacement policy, unchanged in shape from Phase 16H-2, now additionally recording
     * which branch was taken: section adjustment if usable, else station-level fallback if
     * usable, else no historical contribution at all.
     */
    private HistoricalResolutionOutcome resolveHistoricalAdjustment(
            HistoricalDelay historicalDelay, RemainingRouteHistoricalSummary sectionHistoricalSummary) {
        Integer sectionAdjustmentMinutes = sectionHistoricalDelayCalculator.sectionAdjustmentMinutes(sectionHistoricalSummary);
        if (sectionAdjustmentMinutes != null) {
            return new HistoricalResolutionOutcome(
                    sectionAdjustmentMinutes,
                    new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, sectionHistoricalSummary.provenance()));
        }

        Integer stationAdjustmentMinutes = historicalDelayCalculator.historicalAdjustmentMinutes(historicalDelay);
        if (stationAdjustmentMinutes != null) {
            return new HistoricalResolutionOutcome(
                    stationAdjustmentMinutes,
                    new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, historicalDelay.source()));
        }

        return new HistoricalResolutionOutcome(
                0, new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE));
    }

    private record HistoricalResolutionOutcome(int adjustmentMinutes, HistoricalAdjustmentResolution resolution) {
    }

    /**
     * The next-station-scoped counterpart to {@link #resolveHistoricalAdjustment} (Phase 21).
     * <b>Only the immediate next section (index 0 of {@code sectionHistoricalSummary.sectionResults()})
     * may contribute a SECTION-sourced adjustment here</b> - never
     * {@code sectionHistoricalSummary.totalDelayChangeMinutes()}, which sums every remaining
     * section and therefore describes the whole remaining route, not "what happens between here
     * and the next station." This can genuinely resolve to a different source than
     * {@link #resolveHistoricalAdjustment} would (e.g. the immediate section itself has no usable
     * history but a later remaining section does - {@code resolveHistoricalAdjustment} would still
     * report {@code SECTION}, using the later section's contribution, which is entirely legitimate
     * for the destination-scoped total but not for a next-station value) - this method falls back
     * to station-level history in that case instead, exactly as if no section data existed at all.
     *
     * <p>The existing station-level fallback ({@code HistoricalDelayCalculator}) is already
     * correctly next-station-scoped without any change: {@code PredictionService} always fetches
     * {@code HistoricalDelay} for the current section (current station → next station), never the
     * remaining route - see docs/prediction-model.md's Phase 21 notes.
     */
    private HistoricalResolutionOutcome resolveNextStationHistoricalAdjustment(
            HistoricalDelay historicalDelay, RemainingRouteHistoricalSummary sectionHistoricalSummary) {
        List<SectionHistoricalDelayResult> sectionResults = sectionHistoricalSummary.sectionResults();
        if (!sectionResults.isEmpty()) {
            SectionHistoricalDelayResult immediateSection = sectionResults.get(0);
            if (immediateSection.status() == SectionHistoricalDelayStatus.AVAILABLE) {
                int minutes = sectionHistoricalDelayCalculator.weightedMinutes(
                        immediateSection.profile().averageDelayChangeMinutes());
                return new HistoricalResolutionOutcome(
                        minutes,
                        new HistoricalAdjustmentResolution(
                                HistoricalAdjustmentSource.SECTION, immediateSection.profile().source()));
            }
        }

        Integer stationAdjustmentMinutes = historicalDelayCalculator.historicalAdjustmentMinutes(historicalDelay);
        if (stationAdjustmentMinutes != null) {
            return new HistoricalResolutionOutcome(
                    stationAdjustmentMinutes,
                    new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, historicalDelay.source()));
        }

        return new HistoricalResolutionOutcome(
                0, new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE));
    }

    private static void addHistoricalWarnings(
            List<String> warnings,
            HistoricalAdjustmentResolution resolution,
            RemainingRouteHistoricalSummary sectionHistoricalSummary) {
        if (resolution.source() == HistoricalAdjustmentSource.SECTION) {
            if (sectionHistoricalSummary.status() == RemainingRouteHistoricalStatus.PARTIAL_SECTIONS_AVAILABLE) {
                warnings.add("Historical adjustment is based on partial remaining-route section history ("
                        + sectionHistoricalSummary.availableSectionCount() + " of "
                        + sectionHistoricalSummary.sectionResults().size()
                        + " remaining sections) - coverage does not extend to the full remaining route.");
            }
            if (!DataProvenance.RAILRADAR.equals(resolution.provenance())) {
                warnings.add("Section historical delay data is simulated or mixed ("
                        + resolution.provenance()
                        + "), not derived entirely from real historical records.");
            }
            return;
        }

        if (sectionHistoricalSummary.status() == RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE) {
            warnings.add("No usable section-level historical data for the remaining route - "
                    + "falling back to station-level historical data.");
        }
        if (resolution.source() == HistoricalAdjustmentSource.NONE) {
            warnings.add("No historical delay data available for this train/section - historical adjustment is 0.");
        }
    }

    /**
     * Surfaces the one Phase 19 case worth calling out explicitly: a real disruption is known to
     * be active but this policy couldn't quantify it, so it contributes {@code 0} to the
     * arithmetic even though something real is happening - the reader must not mistake that {@code
     * 0} for "nothing is wrong". {@code DATA_UNAVAILABLE} (the default, everyday state while
     * {@code railway-disruption.provider=unavailable}) deliberately does not warn on every single
     * request - that would be noise, not information.
     */
    private static void addDisruptionImpactWarnings(List<String> warnings, DisruptionImpactAssessment assessment) {
        if (assessment.status() == DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE) {
            warnings.add("A real operational disruption is reported for this section but its delay impact "
                    + "could not be estimated - not included in predictedTotalDelayMinutes.");
        } else if (assessment.contributingImpacts().stream()
                .anyMatch(impact -> impact.status() == DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE)) {
            // Some, but not all, active disruptions were estimable - the total already reflects
            // the ones that were, but coverage is only partial.
            warnings.add("At least one active real operational disruption on this section could not be "
                    + "quantified - predictedTotalDelayMinutes reflects only the disruptions that could be.");
        }
        if (assessment.cappedByMaximumAggregate()) {
            warnings.add("Combined real disruption impact exceeded the configured maximum and was capped.");
        }
    }

    /** The "nothing to work with" shape - used by the backward-compatible overload above, and
     * whenever a caller has no remaining-route information at all. Matches exactly what
     * {@code RemainingRouteHistoricalAggregator} itself returns for an {@code UNAVAILABLE} route. */
    private static RemainingRouteHistoricalSummary unavailableSectionHistoricalSummary(String trainNumber) {
        return new RemainingRouteHistoricalSummary(
                trainNumber == null ? "UNKNOWN" : trainNumber,
                RouteCompleteness.UNAVAILABLE,
                List.of(),
                RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS,
                0,
                null,
                DataProvenance.UNAVAILABLE);
    }
}
