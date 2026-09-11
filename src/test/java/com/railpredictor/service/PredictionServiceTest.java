package com.railpredictor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.railpredictor.disruptionimpact.DisruptionImpactAggregator;
import com.railpredictor.evaluation.PredictionSnapshotRecorder;
import com.railpredictor.exception.PredictionNotApplicableException;
import com.railpredictor.exception.TrainNotFoundException;
import com.railpredictor.historical.HistoricalDelayProvider;
import com.railpredictor.historical.RemainingRouteHistoricalAggregator;
import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.RailwayDisruptionAvailability;
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
import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.model.enums.WeatherCondition;
import com.railpredictor.prediction.PredictionEngine;
import com.railpredictor.railradar.TrainDataProvider;
import com.railpredictor.railwaydisruption.RailwayDisruptionProvider;
import com.railpredictor.route.RouteProvider;
import com.railpredictor.route.SectionAnalyzer;
import com.railpredictor.simulation.SimulationEngine;
import com.railpredictor.weather.WeatherProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PredictionServiceTest {

    private final TrainDataProvider trainDataProvider = mock(TrainDataProvider.class);
    private final RouteProvider routeProvider = mock(RouteProvider.class);
    private final WeatherProvider weatherProvider = mock(WeatherProvider.class);
    private final HistoricalDelayProvider historicalDelayProvider = mock(HistoricalDelayProvider.class);
    private final RemainingRouteHistoricalAggregator remainingRouteHistoricalAggregator =
            mock(RemainingRouteHistoricalAggregator.class);
    private final RailwayDisruptionProvider railwayDisruptionProvider = mock(RailwayDisruptionProvider.class);
    private final DisruptionImpactAggregator disruptionImpactAggregator = mock(DisruptionImpactAggregator.class);
    private final SectionAnalyzer sectionAnalyzer = mock(SectionAnalyzer.class);
    private final SimulationEngine simulationEngine = mock(SimulationEngine.class);
    private final PredictionEngine predictionEngine = mock(PredictionEngine.class);
    private final PredictionSnapshotRecorder predictionSnapshotRecorder = mock(PredictionSnapshotRecorder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC);

    private PredictionService service;

    private static final Station CURRENT_STATION = new Station("NDLS", "New Delhi", 28.6, 77.2);
    private static final Station NEXT_STATION = new Station("GZB", "Ghaziabad", 28.7, 77.4);
    private static final RouteSection SECTION = new RouteSection(CURRENT_STATION, NEXT_STATION, null);

    @BeforeEach
    void setUp() {
        service = new PredictionService(
                trainDataProvider, routeProvider, weatherProvider, historicalDelayProvider,
                remainingRouteHistoricalAggregator, railwayDisruptionProvider, disruptionImpactAggregator,
                sectionAnalyzer, simulationEngine, predictionEngine,
                predictionSnapshotRecorder, clock);
        // Default: no real disruption data consulted/available - matches the production default
        // (railway-disruption.provider=unavailable) and keeps every pre-existing test's simulation
        // behavior unchanged unless a test overrides this. A plain thenReturn (not thenAnswer) is
        // deliberate: re-stubbing this same method later (a test overriding it with thenThrow)
        // would otherwise invoke this lambda as a side effect of Mockito recording the new
        // stub - fine for a constant value, not for one that depends on the (here, matcher-null)
        // invocation arguments.
        when(railwayDisruptionProvider.getDisruptions(any(), any(), any())).thenReturn(
                new RailwayDisruptionQueryResult(
                        "12345", SECTION.fromStation().code(), SECTION.toStation().code(),
                        RailwayDisruptionAvailability.UNAVAILABLE, List.of()));
        when(disruptionImpactAggregator.assess(any(), any(), any(), any())).thenReturn(DisruptionImpactAssessment.unavailable());
    }

    private static RemainingRouteHistoricalSummary noSectionHistory(String trainNumber) {
        return new RemainingRouteHistoricalSummary(
                trainNumber, RouteCompleteness.UNAVAILABLE, List.of(),
                RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS, 0, null, DataProvenance.UNAVAILABLE);
    }

    private static LiveTrainData train(Station currentStation, int currentDelayMinutes) {
        return new LiveTrainData("12345", "Test Express", TrainStatus.RUNNING, currentDelayMinutes,
                currentStation, NEXT_STATION, 10.0, 25.0, 80.0);
    }

    private static PredictionResult dummyResult() {
        return new PredictionResult(
                "12345", "Test Express", TrainStatus.RUNNING, CURRENT_STATION, NEXT_STATION, 0,
                10.0, 25.0, 80.0, SectionType.NORMAL, 18.75, 0, 0, 0, 0,
                Instant.parse("2026-09-09T12:00:00Z"),
                new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of(), List.of()),
                List.of(), List.of(), List.of());
    }

    @Test
    void wiresEveryCollaboratorTogetherOnTheHappyPath() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        WeatherData weather = new WeatherData(WeatherCondition.CLEAR, 25.0, 10000.0, 0.0, "mock-provider");
        HistoricalDelay historicalDelay = new HistoricalDelay("12345", SECTION,
                java.time.DayOfWeek.WEDNESDAY, java.time.Month.SEPTEMBER, null, 5.0, 5.0, 0.0, 20, "test-provider");
        SimulationResult simulationResult = new SimulationResult(List.of(), List.of(), 0, 0, 0, 0);
        PredictionResult expected = dummyResult();
        RemainingRoute remainingRoute = new RemainingRoute(
                CURRENT_STATION, NEXT_STATION, List.of(), RouteCompleteness.COMPLETE);
        RemainingRouteHistoricalSummary sectionSummary = noSectionHistory("12345");

        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(routeProvider.remainingRoute(train)).thenReturn(remainingRoute);
        when(remainingRouteHistoricalAggregator.summarize("12345", remainingRoute, Instant.now(clock)))
                .thenReturn(sectionSummary);
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(weather);
        when(historicalDelayProvider.getHistoricalDelay("12345", SECTION)).thenReturn(historicalDelay);
        when(simulationEngine.simulate(any())).thenReturn(simulationResult);
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.NORMAL);
        when(predictionEngine.predict(
                train, SECTION, weather, SectionType.NORMAL, simulationResult, historicalDelay, sectionSummary,
                DisruptionImpactAssessment.unavailable()))
                .thenReturn(expected);

        PredictionResult result = service.getPrediction("12345");

        assertThat(result).isEqualTo(expected);
        verify(weatherProvider).getWeather(28.6, 77.2);
        verify(historicalDelayProvider).getHistoricalDelay("12345", SECTION);
        verify(remainingRouteHistoricalAggregator).summarize("12345", remainingRoute, Instant.now(clock));
        verify(predictionSnapshotRecorder).recordSafely(expected);

        ArgumentCaptor<SimulationContext> contextCaptor = ArgumentCaptor.forClass(SimulationContext.class);
        verify(simulationEngine).simulate(contextCaptor.capture());
        SimulationContext context = contextCaptor.getValue();
        assertThat(context.train()).isEqualTo(train);
        assertThat(context.section()).isEqualTo(SECTION);
        assertThat(context.weather()).isEqualTo(weather);
        assertThat(context.historicalDelay()).isEqualTo(historicalDelay);
        assertThat(context.heavyRainEnabled()).isTrue();
        assertThat(context.denseFogEnabled()).isTrue();
        assertThat(context.highCongestionEnabled()).isTrue();
        assertThat(context.engineeringBlockEnabled()).isTrue();
        assertThat(context.signalHaltEnabled()).isTrue();
        assertThat(context.speedRestrictionKmh()).isNull();
    }

    @Test
    void essentialLiveDataFailurePropagatesUnchanged() {
        when(trainDataProvider.getLiveTrainData("99999")).thenThrow(new TrainNotFoundException("99999"));

        assertThrows(TrainNotFoundException.class, () -> service.getPrediction("99999"));
        verifyNoInteractions(routeProvider, weatherProvider, historicalDelayProvider,
                remainingRouteHistoricalAggregator, sectionAnalyzer, simulationEngine, predictionEngine,
                predictionSnapshotRecorder);
    }

    @Test
    void noNextStationThrowsPredictionNotApplicable() {
        LiveTrainData terminated = new LiveTrainData("12345", "Test Express", TrainStatus.TERMINATED, 0,
                CURRENT_STATION, null, 100.0, null, null);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(terminated);
        when(routeProvider.currentSection(terminated)).thenReturn(Optional.empty());

        assertThrows(PredictionNotApplicableException.class, () -> service.getPrediction("12345"));
        verifyNoInteractions(weatherProvider, historicalDelayProvider,
                remainingRouteHistoricalAggregator, sectionAnalyzer, simulationEngine, predictionEngine,
                predictionSnapshotRecorder);
    }

    @Test
    void missingCoordinatesSkipsTheWeatherCall() {
        Station stationWithoutCoordinates = new Station("NDLS", "New Delhi");
        LiveTrainData train = train(stationWithoutCoordinates, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(
                PredictionServiceTest.noHistoricalDelay());
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        service.getPrediction("12345");

        verify(weatherProvider, never()).getWeather(anyDouble(), anyDouble());
    }

    @Test
    void weatherProviderFailureDegradesGracefullyInsteadOfFailingTheRequest() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(weatherProvider.getWeather(28.6, 77.2)).thenThrow(new RuntimeException("weather API down"));
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(noHistoricalDelay());
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(eq(train), eq(SECTION), eq(null), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        PredictionResult result = service.getPrediction("12345");

        assertThat(result).isEqualTo(dummyResult());
    }

    @Test
    void historicalProviderFailureDegradesToNoHistoricalData() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(null);
        when(historicalDelayProvider.getHistoricalDelay("12345", SECTION))
                .thenThrow(new RuntimeException("database down"));
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        service.getPrediction("12345");

        ArgumentCaptor<SimulationContext> contextCaptor = ArgumentCaptor.forClass(SimulationContext.class);
        verify(simulationEngine).simulate(contextCaptor.capture());
        HistoricalDelay fallback = contextCaptor.getValue().historicalDelay();
        assertThat(fallback.sampleCount()).isZero();
        assertThat(fallback.dayOfWeek()).isEqualTo(java.time.DayOfWeek.WEDNESDAY);
        assertThat(fallback.source()).isEqualTo(com.railpredictor.model.domain.DataProvenance.UNAVAILABLE);
    }

    @Test
    void sectionHistoricalDataFailureDegradesToAnUnavailableSummaryRatherThanFailingTheRequest() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(routeProvider.remainingRoute(train)).thenThrow(new RuntimeException("route lookup failed"));
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(null);
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(noHistoricalDelay());
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        PredictionResult result = service.getPrediction("12345");

        assertThat(result).isEqualTo(dummyResult());
        ArgumentCaptor<RemainingRouteHistoricalSummary> summaryCaptor =
                ArgumentCaptor.forClass(RemainingRouteHistoricalSummary.class);
        verify(predictionEngine).predict(any(), any(), any(), any(), any(), any(), summaryCaptor.capture(), any());
        RemainingRouteHistoricalSummary fallbackSummary = summaryCaptor.getValue();
        assertThat(fallbackSummary.status()).isEqualTo(RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS);
        assertThat(fallbackSummary.totalDelayChangeMinutes()).isNull();
        assertThat(fallbackSummary.provenance()).isEqualTo(com.railpredictor.model.domain.DataProvenance.UNAVAILABLE);
    }

    @Test
    void seedIsStableForTheSameTrainStateAndDiffersWhenStateChanges() {
        LiveTrainData sameStateA = train(CURRENT_STATION, 5);
        LiveTrainData sameStateB = train(CURRENT_STATION, 5);
        LiveTrainData differentState = train(CURRENT_STATION, 20);

        stubForSeedCapture(sameStateA);
        long seedA = captureSeed(sameStateA);
        stubForSeedCapture(sameStateB);
        long seedB = captureSeed(sameStateB);
        stubForSeedCapture(differentState);
        long seedC = captureSeed(differentState);

        assertThat(seedA).isEqualTo(seedB);
        assertThat(seedA).isNotEqualTo(seedC);
    }

    private void stubForSeedCapture(LiveTrainData train) {
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(null);
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(noHistoricalDelay());
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());
    }

    private long captureSeed(LiveTrainData train) {
        service.getPrediction("12345");
        ArgumentCaptor<SimulationContext> contextCaptor = ArgumentCaptor.forClass(SimulationContext.class);
        verify(simulationEngine, times(1)).simulate(contextCaptor.capture());
        long seed = contextCaptor.getValue().randomSeed();
        org.mockito.Mockito.clearInvocations(simulationEngine);
        return seed;
    }

    private static HistoricalDelay noHistoricalDelay() {
        return new HistoricalDelay("12345", SECTION, java.time.DayOfWeek.WEDNESDAY, java.time.Month.SEPTEMBER,
                null, 0, 0, 0, 0, "test-provider");
    }

    // --- Phase 19: railway disruption data / suppression wiring ---

    @Test
    void disruptionDataUnavailableByDefaultLeavesEverySimulatedModelEnabled() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(null);
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(noHistoricalDelay());
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        service.getPrediction("12345");

        ArgumentCaptor<SimulationContext> contextCaptor = ArgumentCaptor.forClass(SimulationContext.class);
        verify(simulationEngine).simulate(contextCaptor.capture());
        SimulationContext context = contextCaptor.getValue();
        assertThat(context.highCongestionEnabled()).isTrue();
        assertThat(context.engineeringBlockEnabled()).isTrue();
        assertThat(context.signalHaltEnabled()).isTrue();

        ArgumentCaptor<DisruptionImpactAssessment> assessmentCaptor = ArgumentCaptor.forClass(DisruptionImpactAssessment.class);
        verify(predictionEngine).predict(any(), any(), any(), any(), any(), any(), any(), assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue()).isEqualTo(DisruptionImpactAssessment.unavailable());
    }

    @Test
    void anActiveRealSignalFailureSuppressesTheSimulatedSignalHaltModelForThisRun() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(null);
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(noHistoricalDelay());
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        com.railpredictor.model.domain.DisruptionImpact signalFailureImpact = new com.railpredictor.model.domain.DisruptionImpact(
                com.railpredictor.model.domain.RailwayDisruptionType.SIGNAL_FAILURE,
                com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 10, "heuristic",
                com.railpredictor.model.domain.DataProvenance.MOCK);
        DisruptionImpactAssessment activeSignalFailure = new DisruptionImpactAssessment(
                com.railpredictor.model.domain.DisruptionImpactStatus.ESTIMATED, 10, List.of(signalFailureImpact), false,
                com.railpredictor.model.domain.CalibrationStatus.INSUFFICIENT_DATA);
        when(disruptionImpactAggregator.assess(any(), any(), any(), any())).thenReturn(activeSignalFailure);

        service.getPrediction("12345");

        ArgumentCaptor<SimulationContext> contextCaptor = ArgumentCaptor.forClass(SimulationContext.class);
        verify(simulationEngine).simulate(contextCaptor.capture());
        SimulationContext context = contextCaptor.getValue();
        assertThat(context.signalHaltEnabled()).isFalse();
        // Unrelated simulated models remain enabled - suppression is per-type, not global.
        assertThat(context.highCongestionEnabled()).isTrue();
        assertThat(context.engineeringBlockEnabled()).isTrue();
        assertThat(context.heavyRainEnabled()).isTrue();
        assertThat(context.denseFogEnabled()).isTrue();

        verify(predictionEngine).predict(any(), any(), any(), any(), any(), any(), any(), eq(activeSignalFailure));
    }

    @Test
    void railwayDisruptionProviderFailureDegradesToUnavailableRatherThanFailingTheRequest() {
        LiveTrainData train = train(CURRENT_STATION, 5);
        when(trainDataProvider.getLiveTrainData("12345")).thenReturn(train);
        when(routeProvider.currentSection(train)).thenReturn(Optional.of(SECTION));
        when(weatherProvider.getWeather(28.6, 77.2)).thenReturn(null);
        when(historicalDelayProvider.getHistoricalDelay(anyString(), any())).thenReturn(noHistoricalDelay());
        when(railwayDisruptionProvider.getDisruptions(any(), any(), any()))
                .thenThrow(new RuntimeException("disruption feed down"));
        when(simulationEngine.simulate(any())).thenReturn(new SimulationResult(List.of(), List.of(), 0, 0, 0, 0));
        when(sectionAnalyzer.analyze(train)).thenReturn(SectionType.CLEAR);
        when(predictionEngine.predict(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(dummyResult());

        PredictionResult result = service.getPrediction("12345");

        assertThat(result).isEqualTo(dummyResult());
        ArgumentCaptor<DisruptionImpactAssessment> assessmentCaptor = ArgumentCaptor.forClass(DisruptionImpactAssessment.class);
        verify(predictionEngine).predict(any(), any(), any(), any(), any(), any(), any(), assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue()).isEqualTo(DisruptionImpactAssessment.unavailable());
    }
}
