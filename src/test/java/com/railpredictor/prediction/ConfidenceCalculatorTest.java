package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentResolution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.WeatherCondition;
import org.junit.jupiter.api.Test;

class ConfidenceCalculatorTest {

    // Weights chosen to sum to 100 for easy-to-read percentages; thresholds match application.properties defaults.
    private static final ConfidenceWeights WEIGHTS = new ConfidenceWeights(15, 10, 15, 10, 15, 10, 25);
    private static final ConfidenceThresholds THRESHOLDS = new ConfidenceThresholds(80, 60, 35);

    private final ConfidenceCalculator calculator = new ConfidenceCalculator(new ConfidenceProperties(WEIGHTS, THRESHOLDS, 1));

    private static HistoricalAdjustmentResolution stationResolution(String provenance) {
        return new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, provenance);
    }

    private static HistoricalAdjustmentResolution noneResolution() {
        return new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE);
    }

    @Test
    void everySignalAvailableAndStableGivesTheMaximumScore() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var resolution = stationResolution("test-provider");
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, resolution, section, simulationResult);

        // All factors satisfied except section-condition reliability, which is always half: 95 + 5 = 95.
        assertThat(score.score()).isEqualTo(95.0);
        assertThat(score.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(score.warnings()).isEmpty();
    }

    @Test
    void missingEverythingGivesOnlyTheStructuralSectionReliabilityCredit() {
        var train = PredictionFixtures.train(0, null, null);
        var resolution = noneResolution();
        var section = PredictionFixtures.section(null);
        // 5 triggered disruptions also exceeds the stable limit of 1, so every conditional
        // factor fails, leaving only the unconditional section-reliability credit.
        var unstableSimulationResult = PredictionFixtures.simulationResultWithTriggeredCount(5);

        var score = calculator.calculate(train, null, resolution, section, unstableSimulationResult);

        // Only section-condition reliability contributes, at half weight: (10*0.5)/100*100 = 5.0
        assertThat(score.score()).isEqualTo(5.0);
        assertThat(score.level()).isEqualTo(ConfidenceLevel.VERY_LOW);
        assertThat(score.warnings()).hasSize(6);
    }

    @Test
    void manySimultaneousDisruptionsAreFlaggedAsUnstable() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var resolution = stationResolution("test-provider");
        var section = PredictionFixtures.section(30.0);
        var unstable = PredictionFixtures.simulationResultWithTriggeredCount(3);

        var score = calculator.calculate(train, weather, resolution, section, unstable);

        assertThat(score.warnings()).anyMatch(w -> w.contains("Multiple simultaneous disruptions"));
        assertThat(score.contributingFactors()).noneMatch(f -> f.contains("stable"));
    }

    @Test
    void triggeredCountExactlyAtTheStableLimitIsStillStable() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var resolution = stationResolution("test-provider");
        var section = PredictionFixtures.section(30.0);
        var atLimit = PredictionFixtures.simulationResultWithTriggeredCount(1);

        var score = calculator.calculate(train, weather, resolution, section, atLimit);

        assertThat(score.contributingFactors()).anyMatch(f -> f.contains("stable"));
    }

    @Test
    void levelBucketsFollowTheConfiguredThresholds() {
        ConfidenceCalculator strictCalculator = new ConfidenceCalculator(
                new ConfidenceProperties(WEIGHTS, new ConfidenceThresholds(90, 70, 50), 1));
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var resolution = stationResolution("test-provider");
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        // score is 95 (as in the first test) but the 90 threshold for HIGH is still met.
        var score = strictCalculator.calculate(train, weather, resolution, section, simulationResult);

        assertThat(score.level()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    void mockHistoricalDataAddsAWarningEvenThoughItStillCountsAsAvailable() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var mockResolution = stationResolution(DataProvenance.MOCK);
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, mockResolution, section, simulationResult);

        // Still counts toward the score exactly as real data would (same as weather's own
        // mock/real handling) - only the warning distinguishes it.
        assertThat(score.score()).isEqualTo(95.0);
        assertThat(score.contributingFactors()).anyMatch(f -> f.contains("source: " + DataProvenance.MOCK));
        assertThat(score.warnings()).anyMatch(w -> w.contains("simulated") && w.contains("mock-provider"));
    }

    @Test
    void realHistoricalDataDoesNotAddTheMockWarning() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var realResolution = stationResolution("postgres");
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, realResolution, section, simulationResult);

        assertThat(score.contributingFactors()).anyMatch(f -> f.contains("source: postgres"));
        assertThat(score.warnings()).noneMatch(w -> w.contains("simulated"));
    }

    @Test
    void sectionSourcedResolutionCountsAsAvailableJustLikeStationFallback() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var sectionResolution = new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR);
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, sectionResolution, section, simulationResult);

        assertThat(score.score()).isEqualTo(95.0);
        assertThat(score.contributingFactors()).anyMatch(f -> f.contains("strategy: SECTION"));
    }

    @Test
    void noneSourceProducesTheNoHistoricalDataWarning() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var resolution = noneResolution();
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, resolution, section, simulationResult);

        assertThat(score.warnings()).anyMatch(w -> w.contains("No historical delay data is available"));
    }

    // --- Phase 16H-4: SECTION vs. STATION_FALLBACK confidence policy ---

    @Test
    void sectionAndStationFallbackReceiveIdenticalScoresUnderOtherwiseIdenticalConditions() {
        // The core Phase 16H-4 decision: no defensible numeric distinction currently exists, so
        // both strategies must score identically, real-provenance-for-real-provenance.
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);
        var sectionResolution = new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR);
        var stationResolution = new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);

        var sectionScore = calculator.calculate(train, weather, sectionResolution, section, simulationResult);
        var stationScore = calculator.calculate(train, weather, stationResolution, section, simulationResult);

        assertThat(sectionScore.score()).isEqualTo(stationScore.score());
        assertThat(sectionScore.level()).isEqualTo(stationScore.level());
        assertThat(sectionScore.warnings()).isEqualTo(stationScore.warnings());
    }

    @Test
    void sectionAndStationFallbackReceiveIdenticalScoresWhenBothAreMockSourced() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);
        var sectionResolution = new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.MOCK);
        var stationResolution = stationResolution(DataProvenance.MOCK);

        var sectionScore = calculator.calculate(train, weather, sectionResolution, section, simulationResult);
        var stationScore = calculator.calculate(train, weather, stationResolution, section, simulationResult);

        assertThat(sectionScore.score()).isEqualTo(stationScore.score());
        assertThat(sectionScore.warnings()).hasSameSizeAs(stationScore.warnings());
    }

    @Test
    void mixedProvenanceIsFlaggedAsPartiallySimulatedEvenThoughItIsNotExactlyMock() {
        // A mixed(...) composite (Phase 16F) is partially mock-derived - it must not be silently
        // treated as fully real just because it isn't an exact match for DataProvenance.MOCK.
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var mixedResolution = new HistoricalAdjustmentResolution(
                HistoricalAdjustmentSource.SECTION, "mixed(mock-provider,railradar)");
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, mixedResolution, section, simulationResult);

        // Score is unaffected - available data still counts, mixed or not - only the warning changes.
        assertThat(score.score()).isEqualTo(95.0);
        assertThat(score.warnings()).anyMatch(w -> w.contains("simulated") && w.contains("mixed("));
    }

    @Test
    void unavailableProvenanceUnderNoneDoesNotAlsoTriggerTheMockWarning() {
        var train = PredictionFixtures.train(0, 60.0, 60.0);
        var weather = PredictionFixtures.weather(WeatherCondition.CLEAR);
        var section = PredictionFixtures.section(30.0);
        var simulationResult = PredictionFixtures.simulationResultWithTriggeredCount(0);

        var score = calculator.calculate(train, weather, noneResolution(), section, simulationResult);

        assertThat(score.warnings()).noneMatch(w -> w.contains("simulated"));
    }
}
