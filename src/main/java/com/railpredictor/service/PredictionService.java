package com.railpredictor.service;

import com.railpredictor.disruptionimpact.DisruptionImpactAggregator;
import com.railpredictor.disruptionimpact.SimulationSuppression;
import com.railpredictor.evaluation.PredictionSnapshotRecorder;
import com.railpredictor.exception.PredictionNotApplicableException;
import com.railpredictor.historical.HistoricalDelayProvider;
import com.railpredictor.historical.RemainingRouteHistoricalAggregator;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RemainingRoute;
import com.railpredictor.model.domain.RemainingRouteHistoricalStatus;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import com.railpredictor.model.domain.RouteCompleteness;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.domain.SimulationResult;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.prediction.PredictionEngine;
import com.railpredictor.railradar.TrainDataProvider;
import com.railpredictor.railwaydisruption.RailwayDisruptionProvider;
import com.railpredictor.route.RouteProvider;
import com.railpredictor.route.SectionAnalyzer;
import com.railpredictor.simulation.SimulationEngine;
import com.railpredictor.weather.WeatherProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The complete prediction pipeline: fetches live data, resolves the current section, gathers
 * weather/historical context (both station-level and, since Phase 16H-2, remaining-route
 * section-level), runs the simulation and section analysis, and assembles the final
 * {@link PredictionResult}. This is the one place all the other components get wired together -
 * none of them know about each other directly, and {@code PredictionEngine} in particular never
 * discovers route topology or queries a historical provider itself - it only ever receives
 * already-resolved domain information.
 *
 * <p>Live train data is essential: if {@link TrainDataProvider} fails, that failure propagates
 * unchanged (fail safely rather than inventing a train). Weather and both kinds of historical data
 * are optional: a failure there is logged and degrades the prediction (missing data, lower
 * confidence, or a fallback to station-level history) rather than failing the whole request - see
 * docs/architecture.md on error handling.
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final TrainDataProvider trainDataProvider;
    private final RouteProvider routeProvider;
    private final WeatherProvider weatherProvider;
    private final HistoricalDelayProvider historicalDelayProvider;
    private final RemainingRouteHistoricalAggregator remainingRouteHistoricalAggregator;
    private final RailwayDisruptionProvider railwayDisruptionProvider;
    private final DisruptionImpactAggregator disruptionImpactAggregator;
    private final SectionAnalyzer sectionAnalyzer;
    private final SimulationEngine simulationEngine;
    private final PredictionEngine predictionEngine;
    private final PredictionSnapshotRecorder predictionSnapshotRecorder;
    private final Clock clock;

    public PredictionService(
            TrainDataProvider trainDataProvider,
            RouteProvider routeProvider,
            WeatherProvider weatherProvider,
            HistoricalDelayProvider historicalDelayProvider,
            RemainingRouteHistoricalAggregator remainingRouteHistoricalAggregator,
            RailwayDisruptionProvider railwayDisruptionProvider,
            DisruptionImpactAggregator disruptionImpactAggregator,
            SectionAnalyzer sectionAnalyzer,
            SimulationEngine simulationEngine,
            PredictionEngine predictionEngine,
            PredictionSnapshotRecorder predictionSnapshotRecorder,
            Clock clock) {
        this.trainDataProvider = trainDataProvider;
        this.routeProvider = routeProvider;
        this.weatherProvider = weatherProvider;
        this.historicalDelayProvider = historicalDelayProvider;
        this.remainingRouteHistoricalAggregator = remainingRouteHistoricalAggregator;
        this.railwayDisruptionProvider = railwayDisruptionProvider;
        this.disruptionImpactAggregator = disruptionImpactAggregator;
        this.sectionAnalyzer = sectionAnalyzer;
        this.simulationEngine = simulationEngine;
        this.predictionEngine = predictionEngine;
        this.predictionSnapshotRecorder = predictionSnapshotRecorder;
        this.clock = clock;
    }

    public PredictionResult getPrediction(String trainNumber) {
        Objects.requireNonNull(trainNumber, "trainNumber");

        // Essential: let a RailRadar failure propagate rather than inventing train data.
        LiveTrainData train = trainDataProvider.getLiveTrainData(trainNumber);

        RouteSection section = routeProvider.currentSection(train)
                .orElseThrow(() -> new PredictionNotApplicableException(trainNumber));

        WeatherData weather = fetchWeather(train.currentStation());
        HistoricalDelay historicalDelay = fetchHistoricalDelay(trainNumber, section);
        RemainingRouteHistoricalSummary sectionHistoricalSummary = fetchSectionHistoricalSummary(trainNumber, train);
        DisruptionImpactAssessment disruptionImpactAssessment =
                fetchDisruptionImpactAssessment(trainNumber, section, train);

        // Phase 19: a real, currently-active disruption suppresses its simulated counterpart for
        // this run - see SimulationSuppression's own Javadoc for why this must never be additive
        // for the same underlying real-world cause. With the default configuration
        // (railway-disruption.provider=unavailable), suppressedTypes is always empty and every
        // simulated model behaves exactly as it did before this phase.
        Set<DisruptionType> suppressedTypes = SimulationSuppression.suppressedSimulationTypes(disruptionImpactAssessment);

        SimulationContext context = new SimulationContext(
                train, section, weather, historicalDelay,
                true, true,
                !suppressedTypes.contains(DisruptionType.HIGH_CONGESTION),
                null,
                !suppressedTypes.contains(DisruptionType.ENGINEERING_BLOCK),
                !suppressedTypes.contains(DisruptionType.SIGNAL_HALT),
                seedFor(train));

        SimulationResult simulationResult = simulationEngine.simulate(context);
        SectionType sectionType = sectionAnalyzer.analyze(train);

        PredictionResult result = predictionEngine.predict(
                train, section, weather, sectionType, simulationResult, historicalDelay, sectionHistoricalSummary,
                disruptionImpactAssessment);

        // Best-effort, disabled by default (Phase 16H-5) - never affects the returned result.
        predictionSnapshotRecorder.recordSafely(result);

        return result;
    }

    /** Weather is optional: unavailable coordinates or a provider failure degrade to no weather. */
    private WeatherData fetchWeather(Station currentStation) {
        Double latitude = currentStation.latitude();
        Double longitude = currentStation.longitude();
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            return weatherProvider.getWeather(latitude, longitude);
        } catch (RuntimeException e) {
            log.warn("Weather unavailable for station {}: {}", currentStation.code(), e.getMessage());
            return null;
        }
    }

    /** Historical data is optional: a provider failure degrades to "no historical data". */
    private HistoricalDelay fetchHistoricalDelay(String trainNumber, RouteSection section) {
        try {
            return historicalDelayProvider.getHistoricalDelay(trainNumber, section);
        } catch (RuntimeException e) {
            log.warn("Historical delay data unavailable for train {}: {}", trainNumber, e.getMessage());
            LocalDate today = LocalDate.now(clock);
            return new HistoricalDelay(
                    trainNumber, section, today.getDayOfWeek(), today.getMonth(), null, 0, 0, 0, 0,
                    DataProvenance.UNAVAILABLE);
        }
    }

    /**
     * Remaining-route section-level historical data (Phase 16H-2) is optional, exactly like
     * station-level history: resolving the remaining route or querying section history can fail
     * (or simply find nothing usable) without affecting live prediction -
     * {@code PredictionEngine} falls back to station-level history whenever this summary carries
     * no usable section data (see {@code SectionHistoricalDelayCalculator}).
     */
    private RemainingRouteHistoricalSummary fetchSectionHistoricalSummary(String trainNumber, LiveTrainData train) {
        try {
            RemainingRoute remainingRoute = routeProvider.remainingRoute(train);
            return remainingRouteHistoricalAggregator.summarize(trainNumber, remainingRoute, Instant.now(clock));
        } catch (RuntimeException e) {
            log.warn("Section historical delay data unavailable for train {}: {}", trainNumber, e.getMessage());
            return unavailableSectionHistoricalSummary(trainNumber);
        }
    }

    /**
     * Real railway operational disruption data (Phase 19) is optional, exactly like weather and
     * historical data: a provider failure (or the default {@code unavailable} provider) degrades
     * to {@link DisruptionImpactAssessment#unavailable()} - it never breaks live prediction, and
     * it never fabricates a "no disruption" result out of a genuine failure.
     */
    private DisruptionImpactAssessment fetchDisruptionImpactAssessment(
            String trainNumber, RouteSection section, LiveTrainData train) {
        try {
            Instant now = Instant.now(clock);
            RailwayDisruptionQueryResult queryResult = railwayDisruptionProvider.getDisruptions(trainNumber, section, now);
            return disruptionImpactAggregator.assess(queryResult, section, train, now);
        } catch (RuntimeException e) {
            log.warn("Railway disruption data unavailable for train {}: {}", trainNumber, e.getMessage());
            return DisruptionImpactAssessment.unavailable();
        }
    }

    private static RemainingRouteHistoricalSummary unavailableSectionHistoricalSummary(String trainNumber) {
        return new RemainingRouteHistoricalSummary(
                trainNumber, RouteCompleteness.UNAVAILABLE, List.of(),
                RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS, 0, null, DataProvenance.UNAVAILABLE);
    }

    /**
     * Derives a simulation seed from the train's current, observable state (not a fixed global
     * constant, and not wall-clock time): the same train reported at the same station with the
     * same delay always simulates the same disruption outcome, but the outcome naturally changes
     * as the train's real situation changes over its journey. An assumption, not a requirement -
     * see docs/architecture.md.
     */
    private static long seedFor(LiveTrainData train) {
        return Objects.hash(train.trainNumber(), train.currentStation().code(), train.currentDelayMinutes());
    }
}
