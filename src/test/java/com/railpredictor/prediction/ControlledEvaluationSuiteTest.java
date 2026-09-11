package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.config.TravelTimeProperties;
import com.railpredictor.model.domain.CascadeEffect;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.SimulationResult;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.WeatherCondition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase 26 CONTROLLED / SYNTHETIC EVALUATION SUITE.
 *
 * <p><b>This suite never touches real production data and produces no real-world accuracy
 * claim.</b> Every input below is hand-constructed and deterministic (a fixed {@link Clock}, no
 * random number generator anywhere in this class) - it exists to verify {@link PredictionEngine}'s
 * safety invariants (bounded, non-negative, deterministic) across a fixed matrix of scenarios, not
 * to measure or report how accurate the system is against real journeys. See
 * docs/evaluation-methodology.md for why this is kept entirely separate from the real
 * {@code PredictionAccuracyReportService}/{@code prediction_snapshots} pipeline - a synthetic
 * result must never be stored, reported, or interpreted as real evaluation evidence.
 *
 * <p>Each scenario is independent and named after the condition it exercises; several conditions
 * already have deep dedicated coverage elsewhere (e.g. {@code PredictionEngineTest}'s NONE/
 * STATION_FALLBACK/SECTION historical-source tests, {@code SimulationEngineTest}'s per-disruption-
 * model tests) - this class is not a replacement for those, it is the one place the full scenario
 * matrix requested for Phase 26 is assembled and asserted together.
 */
class ControlledEvaluationSuiteTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC);
    private static final ConfidenceWeights CONFIDENCE_WEIGHTS = new ConfidenceWeights(15, 10, 15, 10, 15, 10, 25);
    private static final ConfidenceThresholds CONFIDENCE_THRESHOLDS = new ConfidenceThresholds(80, 60, 35);
    private static final int MAX_SANE_DELAY_MINUTES = 600;

    private static PredictionEngine engine() {
        HistoricalAdjustmentProperties historicalAdjustmentProperties = new HistoricalAdjustmentProperties(0.2, 5);
        return new PredictionEngine(
                new TravelTimeCalculator(new TravelTimeProperties(60.0)),
                new DelayCalculator(),
                new HistoricalDelayCalculator(historicalAdjustmentProperties),
                new SectionHistoricalDelayCalculator(historicalAdjustmentProperties),
                new ConfidenceCalculator(new ConfidenceProperties(CONFIDENCE_WEIGHTS, CONFIDENCE_THRESHOLDS, 1)),
                FIXED_CLOCK);
    }

    /** Every scenario must satisfy these regardless of its inputs - the model-safety invariants
     * Phase 26 asks to be guaranteed (no negative delay, no unbounded runaway total). */
    private static void assertSafe(PredictionResult result) {
        assertThat(result.predictedTotalDelayMinutes()).isGreaterThanOrEqualTo(0);
        assertThat(result.predictedNextStationDelayMinutes()).isGreaterThanOrEqualTo(0);
        assertThat(result.predictedTotalDelayMinutes()).isLessThanOrEqualTo(MAX_SANE_DELAY_MINUTES);
        assertThat(result.predictedNextStationDelayMinutes()).isLessThanOrEqualTo(MAX_SANE_DELAY_MINUTES);
        assertThat(result.predictedExtraDelayMinutes()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void zeroDelay() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.CLEAR, PredictionFixtures.simulationResult(0, 0, 0, 0),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.predictedTotalDelayMinutes()).isZero();
    }

    @Test
    void existingDelayCarriesForwardWithoutBeingDoubleCounted() {
        var train = PredictionFixtures.train(45, 60.0, 60.0);
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.CLEAR, PredictionFixtures.simulationResult(0, 0, 0, 0),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(45);
    }

    @Test
    void increasingDelayFromSimulationIsReflectedButBounded() {
        var train = PredictionFixtures.train(10, 60.0, 60.0);
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, PredictionFixtures.simulationResult(30, 0, 0, 30),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(40);
    }

    @Test
    void recoveryReducesNetDelayButNeverBelowZero() {
        var train = PredictionFixtures.train(5, 60.0, 60.0);
        // 20 direct minutes fully recovered - netDelayMinutes reflects that, never negative.
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, PredictionFixtures.simulationResult(20, 0, 20, 0),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(5);
        assertThat(result.recoveryMinutes()).isEqualTo(20);
    }

    @Test
    void heavyRainDisruptionContributesABoundedDelay() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 15, "Simulated heavy rain")),
                List.of(), 15, 0, 0, 15);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.HEAVY_RAIN),
                SectionType.NORMAL, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.disruptions()).extracting(DisruptionResult::type).contains(DisruptionType.HEAVY_RAIN);
    }

    @Test
    void denseFogDisruptionContributesABoundedDelay() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.DENSE_FOG, true, 20, "Simulated dense fog")),
                List.of(), 20, 0, 0, 20);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.DENSE_FOG),
                SectionType.NORMAL, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.disruptions()).extracting(DisruptionResult::type).contains(DisruptionType.DENSE_FOG);
    }

    @Test
    void highCongestionDisruptionContributesABoundedDelay() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.HIGH_CONGESTION, true, 10, "Simulated congestion")),
                List.of(), 10, 0, 0, 10);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.BUSY, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.disruptions()).extracting(DisruptionResult::type).contains(DisruptionType.HIGH_CONGESTION);
    }

    @Test
    void speedRestrictionDisruptionContributesABoundedDelay() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.SPEED_RESTRICTION, true, 10, "Simulated speed restriction")),
                List.of(), 10, 0, 0, 10);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.disruptions()).extracting(DisruptionResult::type).contains(DisruptionType.SPEED_RESTRICTION);
    }

    @Test
    void engineeringBlockDisruptionRespectsItsConfiguredMaximum() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        // Deliberately at the documented default cap (railway-disruption-impact.max-single-
        // disruption-delay-minutes=60) - the simulation model itself is responsible for capping
        // before it ever reaches PredictionEngine; this asserts the engine never re-inflates it.
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.ENGINEERING_BLOCK, true, 60, "Simulated engineering block")),
                List.of(), 60, 0, 0, 60);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(60);
    }

    @Test
    void signalHaltDisruptionContributesABoundedDelay() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.SIGNAL_HALT, true, 8, "Simulated signal halt")),
                List.of(), 8, 0, 0, 8);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.disruptions()).extracting(DisruptionResult::type).contains(DisruptionType.SIGNAL_HALT);
    }

    @Test
    void cascadingDisruptionEffectsRemainBounded() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        List<CascadeEffect> cascade = List.of(
                new CascadeEffect("Simulated: cascade to affected entity #1", 1, 11),
                new CascadeEffect("Simulated: cascade to affected entity #2", 2, 3));
        SimulationResult simulationResult = new SimulationResult(
                List.of(new DisruptionResult(DisruptionType.HIGH_CONGESTION, true, 10, "Simulated congestion")),
                cascade, 10, 14, 0, 24);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.BUSY, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.cascadeEffects()).hasSize(2);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(24);
    }

    @Test
    void combinedDisruptionsAggregateWithoutOverflow() {
        var train = PredictionFixtures.train(5, 60.0, 60.0);
        SimulationResult simulationResult = new SimulationResult(
                List.of(
                        new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 15, "Simulated heavy rain"),
                        new DisruptionResult(DisruptionType.DENSE_FOG, true, 20, "Simulated dense fog"),
                        new DisruptionResult(DisruptionType.HIGH_CONGESTION, true, 10, "Simulated congestion")),
                List.of(), 45, 5, 0, 50);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.HEAVY_RAIN),
                SectionType.BUSY, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.disruptions()).hasSize(3);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(55);
    }

    @Test
    void extremeButBoundedDelayNeverProducesANegativeOrOverflowingResult() {
        // A deliberately extreme, but still int-safe, combination - proves the engine's own
        // Math.max(0, ...) clamp holds even at the edges, without asserting any specific magic cap
        // (bounding is the simulation layer's own configured job, e.g. cascade.max-total-cascade-
        // delay-minutes / railway-disruption-impact.max-aggregate-delay-minutes - not re-enforced
        // a second time inside PredictionEngine itself).
        var train = PredictionFixtures.train(200, 60.0, 60.0);
        SimulationResult simulationResult = PredictionFixtures.simulationResult(200, 0, 0, 200);

        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.BUSY, simulationResult, PredictionFixtures.historicalDelay(0.0, 0));

        assertThat(result.predictedTotalDelayMinutes()).isGreaterThanOrEqualTo(0);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(400);
    }

    @Test
    void unavailableWeatherDegradesGracefullyRatherThanFailing() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), null,
                SectionType.NORMAL, PredictionFixtures.simulationResult(0, 0, 0, 0),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.weatherProvenance()).isNull();
    }

    @Test
    void unavailableDisruptionDataDegradesGracefullyRatherThanFailing() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, PredictionFixtures.simulationResult(0, 0, 0, 0),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        // "unavailable" (never queried at all) is distinct from a genuine 0 - see
        // DisruptionImpactAssessment's own Javadoc on why additionalDelayMinutes is null here.
        assertThat(result.disruptionImpactAssessment().additionalDelayMinutes()).isNull();
    }

    @Test
    void insufficientHistoricalDataResolvesToNoneNeverAFabricatedAdjustment() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        // sampleCount below the configured minimum (5) - must resolve to NONE, not a partial guess.
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(60.0), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, PredictionFixtures.simulationResult(0, 0, 0, 0),
                PredictionFixtures.historicalDelay(25.0, 2));

        assertSafe(result);
        assertThat(result.historicalAdjustmentResolution().source().name()).isEqualTo("NONE");
        assertThat(result.historicalAdjustmentMinutes()).isZero();
    }

    @Test
    void partialRouteInformationStillProducesASafeBoundedResult() {
        // Distance unknown (null) and speed unknown (null) simultaneously - the worst realistic
        // combination of missing live-data fields.
        var train = PredictionFixtures.train(0, null, null);
        PredictionResult result = engine().predict(
                train, PredictionFixtures.section(null), PredictionFixtures.weather(WeatherCondition.CLEAR),
                SectionType.NORMAL, PredictionFixtures.simulationResult(0, 0, 0, 0),
                PredictionFixtures.historicalDelay(0.0, 0));

        assertSafe(result);
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void identicalInputsProduceIdenticalOutputEveryTime() {
        // Determinism check: PredictionEngine has no internal randomness - given the same fixed
        // clock and the same already-computed inputs, it must return byte-for-byte the same result
        // whenever it's called, however many times.
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        PredictionEngine engine = engine();

        PredictionResult first = engine.predict(train, section, weather, SectionType.NORMAL, simulationResult, historicalDelay);
        PredictionResult second = engine.predict(train, section, weather, SectionType.NORMAL, simulationResult, historicalDelay);

        assertThat(first).isEqualTo(second);
    }
}
