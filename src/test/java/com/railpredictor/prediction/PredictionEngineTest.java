package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.config.TravelTimeProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.RemainingRouteHistoricalStatus;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import com.railpredictor.model.domain.RouteCompleteness;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.WeatherCondition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionEngineTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC);
    private static final ConfidenceWeights CONFIDENCE_WEIGHTS = new ConfidenceWeights(15, 10, 15, 10, 15, 10, 25);
    private static final ConfidenceThresholds CONFIDENCE_THRESHOLDS = new ConfidenceThresholds(80, 60, 35);

    private PredictionEngine engine(double assumedSpeedKmh, double historicalWeight, int minimumSampleCount) {
        HistoricalAdjustmentProperties historicalAdjustmentProperties =
                new HistoricalAdjustmentProperties(historicalWeight, minimumSampleCount);
        return new PredictionEngine(
                new TravelTimeCalculator(new TravelTimeProperties(assumedSpeedKmh)),
                new DelayCalculator(),
                new HistoricalDelayCalculator(historicalAdjustmentProperties),
                new SectionHistoricalDelayCalculator(historicalAdjustmentProperties),
                new ConfidenceCalculator(new ConfidenceProperties(CONFIDENCE_WEIGHTS, CONFIDENCE_THRESHOLDS, 1)),
                FIXED_CLOCK);
    }

    @Test
    void matchesTheWorkedExampleFromPredictionModelDocs() {
        // See docs/prediction-model.md "Worked example".
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(train, section, weather, SectionType.NORMAL, simulationResult, historicalDelay);

        assertThat(result.baseTravelTimeMinutes()).isEqualTo(90.0);
        assertThat(result.predictedExtraDelayMinutes()).isEqualTo(8);
        assertThat(result.recoveryMinutes()).isEqualTo(10);
        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(3);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(23);
        assertThat(result.predictedEta()).isEqualTo(Instant.parse("2026-09-09T11:41:00Z"));
    }

    @Test
    void currentDelayIsNotAddedToThePredictedEta() {
        // Two trains with identical remaining journeys but different current delay must get the
        // same ETA - current delay is already reflected in "now", not added again.
        var onTimeTrain = PredictionFixtures.train(0, 60.0, 60.0);
        var lateTrain = PredictionFixtures.train(45, 60.0, 60.0);
        var section = PredictionFixtures.section(60.0);
        var simulationResult = PredictionFixtures.simulationResult(0, 0, 0, 0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult onTimeResult = engine.predict(onTimeTrain, section, null, SectionType.CLEAR, simulationResult, historicalDelay);
        PredictionResult lateResult = engine.predict(lateTrain, section, null, SectionType.CLEAR, simulationResult, historicalDelay);

        assertThat(onTimeResult.predictedEta()).isEqualTo(lateResult.predictedEta());
        assertThat(lateResult.predictedTotalDelayMinutes()).isEqualTo(45);
        assertThat(onTimeResult.predictedTotalDelayMinutes()).isZero();
    }

    @Test
    void warnsWhenDistanceIsUnknown() {
        var train = PredictionFixtures.train(0, null, 60.0);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), PredictionFixtures.historicalDelay(0.0, 10));

        assertThat(result.warnings()).anyMatch(w -> w.contains("Remaining distance unknown"));
    }

    @Test
    void warnsWhenSpeedIsUnavailable() {
        var train = PredictionFixtures.train(0, 60.0, null);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), PredictionFixtures.historicalDelay(0.0, 10));

        assertThat(result.warnings()).anyMatch(w -> w.contains("speed unavailable"));
    }

    @Test
    void warnsWhenNoHistoricalDataIsAvailable() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), PredictionFixtures.historicalDelay(0.0, 0));

        assertThat(result.warnings()).anyMatch(w -> w.contains("No historical delay data"));
    }

    @Test
    void confidenceIsRealAndItsWarningsAreMergedIntoTheTopLevelWarnings() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), PredictionFixtures.historicalDelay(0.0, 0));

        // No weather, no historical data, no route distance -> those show up as confidence warnings too.
        assertThat(result.confidence().warnings()).isNotEmpty();
        assertThat(result.warnings()).containsAll(result.confidence().warnings());
    }

    @Test
    void disruptionsAndCascadeEffectsPassThroughFromTheSimulationResult() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        PredictionEngine engine = engine(60.0, 0.2, 5);
        var simulationResult = PredictionFixtures.simulationResult(10, 0, 0, 10);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.BUSY, simulationResult,
                PredictionFixtures.historicalDelay(0.0, 10));

        assertThat(result.disruptions()).isEqualTo(simulationResult.disruptions());
        assertThat(result.cascadeEffects()).isEqualTo(simulationResult.cascadeEffects());
        assertThat(result.sectionType()).isEqualTo(SectionType.BUSY);
    }

    // --- Phase 16H-2: section history REPLACES (never adds to) station-level history ---

    private static SectionHistoricalDelayResult available(String from, String to, double avgDelayChange, String source) {
        HistoricalSectionDelayProfile profile = new HistoricalSectionDelayProfile(
                "12345", from, to, 15, avgDelayChange, avgDelayChange, 1.0, source, Instant.parse("2026-09-09T09:00:00Z"));
        return new SectionHistoricalDelayResult("12345", from, to, SectionHistoricalDelayStatus.AVAILABLE, profile);
    }

    private static SectionHistoricalDelayResult notFound(String from, String to) {
        return new SectionHistoricalDelayResult("12345", from, to, SectionHistoricalDelayStatus.NOT_FOUND, null);
    }

    private static RemainingRouteHistoricalSummary allAvailable(double total, String provenance) {
        return new RemainingRouteHistoricalSummary(
                "12345", RouteCompleteness.COMPLETE, List.of(available("A", "B", total, provenance)),
                RemainingRouteHistoricalStatus.ALL_SECTIONS_AVAILABLE, 1, total, provenance);
    }

    private static RemainingRouteHistoricalSummary partiallyAvailable(double total) {
        return new RemainingRouteHistoricalSummary(
                "12345", RouteCompleteness.COMPLETE,
                List.of(available("A", "B", total, DataProvenance.RAILRADAR), notFound("B", "C")),
                RemainingRouteHistoricalStatus.PARTIAL_SECTIONS_AVAILABLE, 1, total, DataProvenance.RAILRADAR);
    }

    private static RemainingRouteHistoricalSummary noneAvailable() {
        return new RemainingRouteHistoricalSummary(
                "12345", RouteCompleteness.COMPLETE, List.of(notFound("A", "B")),
                RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE, 0, null, DataProvenance.UNAVAILABLE);
    }

    private static RemainingRouteHistoricalSummary noRemainingSections() {
        return new RemainingRouteHistoricalSummary(
                "12345", RouteCompleteness.UNAVAILABLE, List.of(),
                RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS, 0, null, DataProvenance.UNAVAILABLE);
    }

    @Test
    void whenAllSectionsAvailableTheSectionAdjustmentReplacesTheStationOneEntirely() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        // Station-level would give round(20 * 0.2) = 4 if used - it must NOT be used.
        var historicalDelay = PredictionFixtures.historicalDelay(20.0, 40);
        var summary = allAvailable(10.0, DataProvenance.RAILRADAR); // section total = +10
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        // round(10 * 0.2) = 2, NOT round(20 * 0.2) = 4 - proves replacement, not addition.
        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(2);
        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.SECTION);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void aNegativeSectionTotalProducesANegativeAdjustmentNotClampedToZero() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0);
        var summary = allAvailable(-20.0, DataProvenance.RAILRADAR);
        PredictionEngine engine = engine(60.0, 0.5, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(-10);
    }

    @Test
    void partialSectionAvailabilityStillContributesAndWarnsAboutIncompleteCoverage() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0);
        var summary = partiallyAvailable(8.0);
        PredictionEngine engine = engine(60.0, 1.0, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(8);
        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.SECTION);
        assertThat(result.warnings()).anyMatch(w -> w.contains("partial remaining-route section history"));
    }

    @Test
    void mockOrMixedSectionProvenanceProducesAnExplicitWarning() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0);
        var summary = allAvailable(5.0, DataProvenance.MOCK);
        PredictionEngine engine = engine(60.0, 1.0, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.warnings()).anyMatch(w -> w.contains("simulated or mixed"));
    }

    @Test
    void realSectionProvenanceProducesNoSimulatedDataWarning() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0);
        var summary = allAvailable(5.0, DataProvenance.RAILRADAR);
        PredictionEngine engine = engine(60.0, 1.0, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.warnings()).noneMatch(w -> w.contains("simulated or mixed"));
    }

    @Test
    void noSectionsAvailableFallsBackToStationLevelHistoryAndWarnsAboutTheFallback() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40); // round(15*0.2)=3
        var summary = noneAvailable();
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(3);
        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.STATION_FALLBACK);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(historicalDelay.source());
        assertThat(result.warnings()).anyMatch(w -> w.contains("falling back to station-level historical data"));
    }

    @Test
    void noRemainingSectionsFallsBackSilentlyWithoutAFallbackWarning() {
        // Already-arrived / route-unavailable is not a data-quality problem worth a warning of
        // its own - only genuine "sections existed but none were usable" gets the fallback notice.
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        var summary = noRemainingSections();
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(3);
        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.STATION_FALLBACK);
        assertThat(result.warnings()).noneMatch(w -> w.contains("falling back to station-level historical data"));
    }

    @Test
    void neitherSectionNorStationUsableProducesNoneSourceAndZeroAdjustment() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0); // below minimum -> unusable
        var summary = noneAvailable();
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentMinutes()).isZero();
        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.NONE);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(DataProvenance.UNAVAILABLE);
        assertThat(result.warnings()).anyMatch(w -> w.contains("historical adjustment is 0"));
    }

    @Test
    void mixedSectionProvenanceIsPreservedOnTheResolutionExactly() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(0.0, 0);
        String mixed = "mixed(mock-provider,railradar)";
        var summary = allAvailable(5.0, mixed);
        PredictionEngine engine = engine(60.0, 1.0, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.SECTION);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(mixed);
    }

    @Test
    void stationFallbackMockProvenanceIsPreservedOnTheResolutionExactly() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40, DataProvenance.MOCK);
        var summary = noneAvailable();
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, PredictionFixtures.section(), null, SectionType.CLEAR,
                PredictionFixtures.simulationResult(0, 0, 0, 0), historicalDelay, summary);

        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.STATION_FALLBACK);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(DataProvenance.MOCK);
    }

    @Test
    void arithmeticRegressionWorkedExampleCountsHistoricalAdjustmentExactlyOnce() {
        // Same worked example as docs/prediction-model.md, now routed through section history
        // instead of station history, proving the arithmetic (not just the source) is unchanged:
        // predictedTotalDelayMinutes = max(0, currentDelay + predictedExtraDelay + historicalAdjustment)
        //                            = max(0, 12 + 8 + 3) = 23
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        // Station-level would ALSO give round(15*0.2)=3 if used - deliberately identical so the
        // test proves the figure is applied exactly once, regardless of which source it came from.
        var historicalDelay = PredictionFixtures.historicalDelay(999.0, 999);
        var summary = allAvailable(15.0, DataProvenance.RAILRADAR);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(train, section, weather, SectionType.NORMAL, simulationResult, historicalDelay, summary);

        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.SECTION);
        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(3);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(23);
        assertThat(result.predictedEta()).isEqualTo(Instant.parse("2026-09-09T11:41:00Z"));
    }

    @Test
    void theLegacySixArgumentOverloadBehavesExactlyLikeBeforeThisPhaseAndReportsStationFallback() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(train, section, weather, SectionType.NORMAL, simulationResult, historicalDelay);

        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(3);
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(23);
        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.STATION_FALLBACK);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(historicalDelay.source());
    }

    // --- Phase 17: weatherProvenance ---

    @Test
    void weatherProvenanceIsCapturedFromTheSuppliedWeatherData() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(train, section, weather, SectionType.NORMAL, simulationResult, historicalDelay);

        assertThat(result.weatherProvenance()).isEqualTo("mock-provider");
    }

    @Test
    void weatherProvenanceIsNullWhenNoWeatherDataWasAvailable() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(train, section, null, SectionType.NORMAL, simulationResult, historicalDelay);

        assertThat(result.weatherProvenance()).isNull();
    }

    // --- Phase 19: disruptionImpactAssessment / no double-counting ---

    @Test
    void theLegacySevenArgumentOverloadDefaultsToTheUnavailableDisruptionImpactAssessment() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(train, section, null, SectionType.NORMAL, simulationResult, historicalDelay);

        assertThat(result.disruptionImpactAssessment())
                .isEqualTo(com.railpredictor.model.domain.DisruptionImpactAssessment.unavailable());
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(12 + 8 + 3);
    }

    @Test
    void anEstimatedDisruptionImpactIsAddedExactlyOnceToTheTotalAndEta() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        var noSectionHistory = unavailableSectionSummary("12952");
        PredictionEngine engine = engine(60.0, 0.2, 5);

        com.railpredictor.model.domain.DisruptionImpact contributing = new com.railpredictor.model.domain.DisruptionImpact(
                com.railpredictor.model.domain.RailwayDisruptionType.ENGINEERING_BLOCK,
                com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 20, "heuristic", DataProvenance.MOCK);
        com.railpredictor.model.domain.DisruptionImpactAssessment assessment =
                new com.railpredictor.model.domain.DisruptionImpactAssessment(
                        com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 20, List.of(contributing), false,
                        com.railpredictor.model.domain.CalibrationStatus.INSUFFICIENT_DATA);

        PredictionResult result = engine.predict(
                train, section, null, SectionType.NORMAL, simulationResult, historicalDelay, noSectionHistory, assessment);

        // currentDelay(12) + predictedExtraDelay(8, from simulation net delay) + historical(3) + disruption(20)
        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(12 + 8 + 3 + 20);
        assertThat(result.disruptionImpactAssessment()).isEqualTo(assessment);
    }

    @Test
    void aDataUnavailableDisruptionAssessmentContributesZeroNeverFabricatingANumber() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        var noSectionHistory = unavailableSectionSummary("12952");
        PredictionEngine engine = engine(60.0, 0.2, 5);

        PredictionResult result = engine.predict(
                train, section, null, SectionType.NORMAL, simulationResult, historicalDelay, noSectionHistory,
                com.railpredictor.model.domain.DisruptionImpactAssessment.unavailable());

        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(12 + 8 + 3);
    }

    @Test
    void aPresentButNotEstimableDisruptionAssessmentContributesZeroAndWarns() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        var noSectionHistory = unavailableSectionSummary("12952");
        PredictionEngine engine = engine(60.0, 0.2, 5);

        com.railpredictor.model.domain.DisruptionImpact notEstimable = new com.railpredictor.model.domain.DisruptionImpact(
                com.railpredictor.model.domain.RailwayDisruptionType.ROUTE_DIVERSION,
                com.railpredictor.model.domain.DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null, "unknown", DataProvenance.MOCK);
        com.railpredictor.model.domain.DisruptionImpactAssessment assessment =
                new com.railpredictor.model.domain.DisruptionImpactAssessment(
                        com.railpredictor.model.domain.DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null,
                        List.of(notEstimable), false, com.railpredictor.model.domain.CalibrationStatus.INSUFFICIENT_DATA);

        PredictionResult result = engine.predict(
                train, section, null, SectionType.NORMAL, simulationResult, historicalDelay, noSectionHistory, assessment);

        assertThat(result.predictedTotalDelayMinutes()).isEqualTo(12 + 8 + 3);
        assertThat(result.warnings()).anyMatch(w -> w.contains("could not be estimated"));
    }

    @Test
    void aCappedAggregateProducesAWarning() {
        var train = PredictionFixtures.train(12, 90.0, 60.0);
        var section = PredictionFixtures.section(90.0);
        var simulationResult = PredictionFixtures.simulationResult(15, 3, 10, 8);
        var historicalDelay = PredictionFixtures.historicalDelay(15.0, 40);
        var noSectionHistory = unavailableSectionSummary("12952");
        PredictionEngine engine = engine(60.0, 0.2, 5);

        com.railpredictor.model.domain.DisruptionImpact contributing = new com.railpredictor.model.domain.DisruptionImpact(
                com.railpredictor.model.domain.RailwayDisruptionType.ENGINEERING_BLOCK,
                com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 60, "capped", DataProvenance.MOCK);
        com.railpredictor.model.domain.DisruptionImpactAssessment cappedAssessment =
                new com.railpredictor.model.domain.DisruptionImpactAssessment(
                        com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 60, List.of(contributing), true,
                        com.railpredictor.model.domain.CalibrationStatus.INSUFFICIENT_DATA);

        PredictionResult result = engine.predict(
                train, section, null, SectionType.NORMAL, simulationResult, historicalDelay, noSectionHistory, cappedAssessment);

        assertThat(result.warnings()).anyMatch(w -> w.contains("capped"));
    }

    private static RemainingRouteHistoricalSummary unavailableSectionSummary(String trainNumber) {
        return new RemainingRouteHistoricalSummary(
                trainNumber, RouteCompleteness.UNAVAILABLE, List.of(),
                RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS, 0, null, DataProvenance.UNAVAILABLE);
    }
}
