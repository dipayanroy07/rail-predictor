package com.railpredictor.prediction;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.HistoricalAdjustmentResolution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationResult;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.ConfidenceLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Scores how reliable a prediction is likely to be, based on how much of its input data was
 * actually available and how stable the simulation was - not a statistically calibrated
 * probability that the prediction will turn out accurate (see docs/architecture.md).
 *
 * <p>Each factor below contributes a configurable weight to the score if satisfied; the final
 * score is the achieved weight as a percentage of the total possible weight, so
 * {@code prediction.confidence.weights.*} need not sum to 100.
 *
 * <p>Since Phase 16H-3, the historical-availability factor is judged from the already-resolved
 * {@link HistoricalAdjustmentResolution} rather than the raw station-level {@code HistoricalDelay}
 * - {@code historicalAdjustmentMinutes} can now come from section-level history, so checking only
 * the station-level sample count would misreport "no historical data" for a prediction that
 * genuinely used real section data. The weights, thresholds, and overall scoring formula are
 * unchanged - only which existing signal decides whether this one factor is satisfied.
 *
 * <p><b>Phase 16H-4 policy decision: {@code SECTION} and {@code STATION_FALLBACK} receive
 * identical numerical treatment</b> - both satisfy the {@code historicalDataAvailability} factor
 * equally, with no multiplier distinguishing them. This was a deliberate choice, not an oversight:
 * no calibrated evidence exists in this codebase (no backtesting/accuracy-tracking
 * infrastructure) from which a defensible numeric difference could be derived, and the one real,
 * already-established asymmetry between the two - section-level delay-change samples needing a
 * higher minimum sample count than station-level ones (`historical.section.minimum-sample-count`,
 * default 10, vs. `prediction.historical-adjustment.minimum-sample-count`, default 5 - see
 * docs/historical-data-design.md's Phase 16G notes on the variance-of-a-difference reasoning) - is
 * already fully accounted for by that gate itself: by the time either source reaches this
 * calculator, it has already cleared its own bar for "usable." Discounting it again here would be
 * double-counting an already-applied threshold, not adding new information. See
 * docs/prediction-model.md's "Phase 16H-4" section for the full policy and what evidence would be
 * required to justify a future numeric distinction.
 *
 * <p>Similarly, {@code PARTIAL_SECTIONS_AVAILABLE} coverage (some but not all remaining sections
 * had usable history) is <b>not</b> factored into this score - `PredictionResult` already exposes
 * `RemainingRouteHistoricalSummary`'s section-by-section detail and
 * `PredictionEngine`'s own partial-coverage warning, so nothing about that signal is lost; this
 * calculator simply doesn't yet turn it into a numeric factor, since no evidence supports a
 * particular coverage-to-confidence relationship.
 */
@Component
public class ConfidenceCalculator {

    private final ConfidenceProperties properties;

    public ConfidenceCalculator(ConfidenceProperties properties) {
        this.properties = properties;
    }

    public ConfidenceScore calculate(
            LiveTrainData train,
            WeatherData weather,
            HistoricalAdjustmentResolution historicalResolution,
            RouteSection section,
            SimulationResult simulationResult) {
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(historicalResolution, "historicalResolution");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(simulationResult, "simulationResult");

        ConfidenceWeights weights = properties.weights();
        List<String> factors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double achieved = 0;
        double possible = 0;

        possible += weights.liveTrainDataCompleteness();
        if (train.nextStation() != null && train.remainingDistanceKm() != null) {
            achieved += weights.liveTrainDataCompleteness();
            factors.add("Live train data is complete (next station and remaining distance known)");
        } else {
            warnings.add("Live train data is incomplete (missing next station or remaining distance)");
        }

        possible += weights.weatherAvailability();
        if (weather != null) {
            achieved += weights.weatherAvailability();
            factors.add("Weather data is available (" + weather.source() + ")");
        } else {
            warnings.add("Weather data is unavailable");
        }

        possible += weights.historicalDataAvailability();
        if (historicalResolution.source() != HistoricalAdjustmentSource.NONE) {
            // SECTION and STATION_FALLBACK score identically here - see this class's own Javadoc
            // ("Phase 16H-4 policy decision") for why no numeric distinction is currently justified.
            achieved += weights.historicalDataAvailability();
            factors.add("Historical delay data is available (strategy: " + historicalResolution.source()
                    + ", source: " + historicalResolution.provenance() + ")");
            // .contains(...), not equals(...): a mixed(...) provenance composite (Phase 16F) is
            // partially mock-derived and must not be silently treated as fully real either - the
            // score is unaffected (available data still counts, mock or real), only the warning.
            if (historicalResolution.provenance().contains(DataProvenance.MOCK)) {
                warnings.add("Historical delay data is partially or fully simulated ("
                        + historicalResolution.provenance() + "), not derived entirely from real historical records");
            }
        } else {
            warnings.add("No historical delay data is available for this train/section");
        }

        possible += weights.routeInformationQuality();
        if (section.distanceKm() != null) {
            achieved += weights.routeInformationQuality();
            factors.add("Route section distance is known");
        } else {
            warnings.add("Route section distance is unknown, limiting route information quality");
        }

        possible += weights.speedAvailability();
        if (train.speedKmh() != null) {
            achieved += weights.speedAvailability();
            factors.add("Train speed is reported");
        } else {
            warnings.add("Train speed is unavailable");
        }

        // Structural, not per-request: the section-condition estimate (Phase 4) is derived from
        // delay alone, never real occupancy data, so it's only ever partially reliable.
        possible += weights.sectionConditionReliability();
        achieved += weights.sectionConditionReliability() * 0.5;
        factors.add("Section condition is estimated from delay alone, not real occupancy data (partial reliability)");

        possible += weights.simulationStability();
        long triggeredDisruptions = simulationResult.disruptions().stream().filter(DisruptionResult::triggered).count();
        if (triggeredDisruptions <= properties.maxStableTriggeredDisruptions()) {
            achieved += weights.simulationStability();
            factors.add("Simulation is stable (" + triggeredDisruptions + " disruption(s) triggered)");
        } else {
            warnings.add("Multiple simultaneous disruptions triggered (" + triggeredDisruptions
                    + "), compounding uncertainty in the simulated delay");
        }

        double score = possible <= 0 ? 0.0 : Math.round((achieved / possible) * 1000.0) / 10.0;
        return new ConfidenceScore(score, levelFor(score), List.copyOf(factors), List.copyOf(warnings));
    }

    private ConfidenceLevel levelFor(double score) {
        ConfidenceThresholds thresholds = properties.thresholds();
        if (score >= thresholds.highMinimumScore()) {
            return ConfidenceLevel.HIGH;
        }
        if (score >= thresholds.mediumMinimumScore()) {
            return ConfidenceLevel.MEDIUM;
        }
        if (score >= thresholds.lowMinimumScore()) {
            return ConfidenceLevel.LOW;
        }
        return ConfidenceLevel.VERY_LOW;
    }
}
