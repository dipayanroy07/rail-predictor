package com.railpredictor.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.railpredictor.config.CascadeProperties;
import com.railpredictor.config.ConfidenceProperties;
import com.railpredictor.config.ConfidenceThresholds;
import com.railpredictor.config.ConfidenceWeights;
import com.railpredictor.config.DisruptionImpactProperties;
import com.railpredictor.config.DisruptionProperties;
import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.config.HistoricalMockProperties;
import com.railpredictor.config.ProbabilityDelayConfig;
import com.railpredictor.config.RecoveryProperties;
import com.railpredictor.config.SectionAnalysisProperties;
import com.railpredictor.config.SpeedRestrictionConfig;
import com.railpredictor.config.TravelTimeProperties;
import com.railpredictor.config.WeatherMockProperties;
import com.railpredictor.config.HistoricalSectionMockProperties;
import com.railpredictor.config.HistoricalSectionProperties;
import com.railpredictor.config.PredictionEvaluationProperties;
import com.railpredictor.disruptionimpact.DisruptionImpactAggregator;
import com.railpredictor.disruptionimpact.HeuristicDisruptionImpactPolicy;
import com.railpredictor.evaluation.PredictionSnapshotRecorder;
import com.railpredictor.historical.MockHistoricalDelayProvider;
import com.railpredictor.historical.MockSectionHistoricalDelayProvider;
import com.railpredictor.historical.RemainingRouteHistoricalAggregator;
import com.railpredictor.railwaydisruption.RailwayDisruptionProvider;
import com.railpredictor.railwaydisruption.UnavailableRailwayDisruptionProvider;
import com.railpredictor.repository.PredictionSnapshotEntityMapper;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.dto.PredictionOutput;
import com.railpredictor.model.dto.PredictionOutputMapper;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.model.enums.WeatherCondition;
import com.railpredictor.prediction.ConfidenceCalculator;
import com.railpredictor.prediction.DelayCalculator;
import com.railpredictor.prediction.HistoricalDelayCalculator;
import com.railpredictor.prediction.PredictionEngine;
import com.railpredictor.prediction.SectionHistoricalDelayCalculator;
import com.railpredictor.prediction.TravelTimeCalculator;
import com.railpredictor.railradar.TrainDataProvider;
import com.railpredictor.route.LiveDataRouteProvider;
import com.railpredictor.route.RouteProvider;
import com.railpredictor.route.SectionAnalyzer;
import com.railpredictor.route.DelayBasedSectionAnalyzer;
import com.railpredictor.service.PredictionService;
import com.railpredictor.simulation.CascadeEngine;
import com.railpredictor.simulation.CongestionModel;
import com.railpredictor.simulation.DenseFogModel;
import com.railpredictor.simulation.DisruptionModel;
import com.railpredictor.simulation.EngineeringBlockModel;
import com.railpredictor.simulation.HeavyRainModel;
import com.railpredictor.simulation.RecoveryModel;
import com.railpredictor.simulation.SignalHaltModel;
import com.railpredictor.simulation.SimulationEngine;
import com.railpredictor.simulation.SpeedRestrictionModel;
import com.railpredictor.weather.MockWeatherProvider;
import com.railpredictor.weather.WeatherProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A development/test mechanism: runs the complete prediction pipeline (Phases 3-13), wired with
 * the same mock providers and default-like configuration as {@code application.properties}, end
 * to end for one fixed train, and writes the resulting JSON to {@code prediction_output.json} at
 * the project root - so the JSON shape can be inspected without needing a REST API (Phase 15
 * doesn't exist yet). Also serves as this phase's integration test: it fails if the pipeline
 * cannot produce a valid, parseable prediction JSON.
 *
 * <p>The only non-real component here is {@code TrainDataProvider}: a fixed in-memory train
 * (rather than a real RailRadar call) is the one deliberate substitution, so this test is
 * deterministic and needs no network access or API key. Weather is configured as
 * {@code HEAVY_RAIN} so the simulation's heavy-rain disruption triggers deterministically (see
 * {@code HeavyRainModel}'s weather-corroboration behaviour) rather than depending on a random
 * probability roll, making the generated example JSON illustrative rather than empty.
 */
class PredictionOutputGenerationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC);

    private static LiveTrainData fixedTrain() {
        Station kota = new Station("KOTA", "Kota Jn", 25.18, 75.83);
        Station ratlam = new Station("RTM", "Ratlam Jn", 23.33, 75.04);
        return new LiveTrainData(
                "12952", "New Delhi - Mumbai Central Rajdhani Express", TrainStatus.RUNNING, 12,
                kota, ratlam, 465.0, 919.0, 92.5);
    }

    private static PredictionService buildPipeline() {
        TrainDataProvider trainDataProvider = trainNumber -> fixedTrain();
        RouteProvider routeProvider = new LiveDataRouteProvider();
        WeatherProvider weatherProvider = new MockWeatherProvider(
                new WeatherMockProperties(WeatherCondition.HEAVY_RAIN, 22.0, 800.0, 15.0));
        MockHistoricalDelayProvider historicalDelayProvider = new MockHistoricalDelayProvider(
                new HistoricalMockProperties(8.0, 6.0, 3.0, 40, "EVENING_PEAK"), CLOCK);
        // fixedTrain() below still uses the pre-Phase-16H-1 LiveTrainData constructor, so it
        // carries no remaining-route stops - remainingRoute() resolves to UNAVAILABLE and this
        // pipeline exercises (and this test continues to illustrate) the station-level fallback
        // path, proving Phase 16H-2's replacement wiring is present without changing this test's
        // own long-standing expected JSON shape/values. The configured statistics below are never
        // actually queried as a result, but a real HistoricalSectionDelayProvider must still be
        // wired for the pipeline to be genuine end-to-end wiring, not a stub.
        MockSectionHistoricalDelayProvider sectionHistoricalDelayProvider = new MockSectionHistoricalDelayProvider(
                new HistoricalSectionMockProperties(0, 0, 0, 0), new HistoricalSectionProperties(10), CLOCK);
        RemainingRouteHistoricalAggregator remainingRouteHistoricalAggregator =
                new RemainingRouteHistoricalAggregator(sectionHistoricalDelayProvider);
        SectionAnalyzer sectionAnalyzer = new DelayBasedSectionAnalyzer(new SectionAnalysisProperties(10, 30));

        DisruptionProperties disruptionProperties = new DisruptionProperties(
                new ProbabilityDelayConfig(0.5, 5, 20),
                new ProbabilityDelayConfig(0.5, 10, 30),
                new ProbabilityDelayConfig(0.4, 5, 15),
                new ProbabilityDelayConfig(0.3, 15, 45),
                new ProbabilityDelayConfig(0.3, 3, 10),
                new SpeedRestrictionConfig(10));
        List<DisruptionModel> disruptionModels = List.of(
                new HeavyRainModel(disruptionProperties),
                new DenseFogModel(disruptionProperties),
                new CongestionModel(disruptionProperties),
                new EngineeringBlockModel(disruptionProperties),
                new SignalHaltModel(disruptionProperties),
                new SpeedRestrictionModel(disruptionProperties));
        CascadeEngine cascadeEngine = new CascadeEngine(new CascadeProperties(10, 0.3, 3, 30, 3));
        RecoveryModel recoveryModel = new RecoveryModel(new RecoveryProperties(0.2, 0.5, 0.05, 10));
        SimulationEngine simulationEngine = new SimulationEngine(disruptionModels, cascadeEngine, recoveryModel);

        HistoricalAdjustmentProperties historicalAdjustmentProperties = new HistoricalAdjustmentProperties(0.2, 5);
        PredictionEngine predictionEngine = new PredictionEngine(
                new TravelTimeCalculator(new TravelTimeProperties(60.0)),
                new DelayCalculator(),
                new HistoricalDelayCalculator(historicalAdjustmentProperties),
                new SectionHistoricalDelayCalculator(historicalAdjustmentProperties),
                new ConfidenceCalculator(new ConfidenceProperties(
                        new ConfidenceWeights(15, 10, 15, 10, 15, 10, 25),
                        new ConfidenceThresholds(80, 60, 35),
                        1)),
                CLOCK);

        PredictionSnapshotRecorder predictionSnapshotRecorder = new PredictionSnapshotRecorder(
                java.util.Optional.empty(), new PredictionSnapshotEntityMapper(),
                new PredictionEvaluationProperties(false), CLOCK);

        RailwayDisruptionProvider railwayDisruptionProvider = new UnavailableRailwayDisruptionProvider();
        DisruptionImpactAggregator disruptionImpactAggregator = new DisruptionImpactAggregator(
                new HeuristicDisruptionImpactPolicy(new DisruptionImpactProperties(15, 10, 10, 60, 90)),
                new DisruptionImpactProperties(15, 10, 10, 60, 90));

        return new PredictionService(
                trainDataProvider, routeProvider, weatherProvider, historicalDelayProvider,
                remainingRouteHistoricalAggregator, railwayDisruptionProvider, disruptionImpactAggregator,
                sectionAnalyzer, simulationEngine, predictionEngine,
                predictionSnapshotRecorder, CLOCK);
    }

    @Test
    void generatesAValidPredictionOutputJsonFile() throws IOException {
        PredictionService pipeline = buildPipeline();
        PredictionResult result = pipeline.getPrediction("12952");
        PredictionOutput output = new PredictionOutputMapper().toOutput(result);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        String json = objectMapper.writeValueAsString(output);

        File outputFile = new File(System.getProperty("user.dir"), "prediction_output.json");
        Files.writeString(outputFile.toPath(), json);

        assertThat(outputFile).exists();

        // The pipeline must produce a valid, parseable prediction - not just any file contents.
        JsonNode parsed = objectMapper.readTree(json);
        assertThat(parsed.get("trainNumber").asText()).isEqualTo("12952");
        assertThat(parsed.get("trainName").asText()).isEqualTo("New Delhi - Mumbai Central Rajdhani Express");
        assertThat(parsed.get("currentStation").get("code").asText()).isEqualTo("KOTA");
        assertThat(parsed.get("nextStation").get("code").asText()).isEqualTo("RTM");
        assertThat(parsed.has("prediction")).isTrue();
        assertThat(parsed.has("simulation")).isTrue();
        assertThat(parsed.has("confidence")).isTrue();
        assertThat(parsed.get("prediction").get("predictedEta").asText()).isNotBlank();
        assertThat(parsed.get("confidence").get("score").asDouble()).isBetween(0.0, 100.0);

        // Heavy rain was configured as the actual weather reading, so it must have triggered
        // deterministically (see HeavyRainModel) - proves the simulation genuinely ran, not a stub.
        boolean heavyRainTriggered = false;
        for (JsonNode disruption : parsed.get("simulation").get("triggeredDisruptions")) {
            if ("HEAVY_RAIN".equals(disruption.get("disruptionType").asText())) {
                heavyRainTriggered = true;
            }
        }
        assertThat(heavyRainTriggered).isTrue();

        // Round-trips back into the same typed record, not just "some JSON".
        PredictionOutput roundTripped = objectMapper.readValue(json, PredictionOutput.class);
        assertThat(roundTripped).isEqualTo(output);
    }
}
