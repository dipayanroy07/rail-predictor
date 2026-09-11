package com.railpredictor.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.CascadeEffect;
import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.HistoricalAdjustmentResolution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionOutputMapperTest {

    private final PredictionOutputMapper mapper = new PredictionOutputMapper();

    private static Station station(String code) {
        return new Station(code, code + " Station", 28.6, 77.2);
    }

    private static PredictionResult result(List<DisruptionResult> disruptions, Station nextStation) {
        return new PredictionResult(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, station("KOTA"), nextStation,
                12, 465.0, 919.0, 92.5, SectionType.NORMAL, 90.0, 8, 3, 10, 23,
                Instant.parse("2026-09-09T11:41:00Z"),
                new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of("speed data available"), List.of("weather unavailable")),
                disruptions,
                List.of(new CascadeEffect("Simulated: increased occupancy propagates delay", 1, 5)),
                List.of("No historical delay data available for this train/section - historical adjustment is 0."),
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR));
    }

    @Test
    void mapsTopLevelFieldsDirectlyFromTheDomainResult() {
        PredictionResult domainResult = result(List.of(), station("RTM"));

        PredictionOutput output = mapper.toOutput(domainResult);

        assertThat(output.trainNumber()).isEqualTo("12952");
        assertThat(output.trainName()).isEqualTo("Rajdhani Express");
        assertThat(output.currentStatus()).isEqualTo(TrainStatus.RUNNING);
        assertThat(output.currentDelayMinutes()).isEqualTo(12);
        assertThat(output.currentStation().code()).isEqualTo("KOTA");
        assertThat(output.nextStation().code()).isEqualTo("RTM");
        assertThat(output.distanceFromOriginKm()).isEqualTo(465.0);
        assertThat(output.remainingDistanceKm()).isEqualTo(919.0);
        assertThat(output.estimatedSpeedKmh()).isEqualTo(92.5);
        assertThat(output.estimatedSectionCondition()).isEqualTo(SectionType.NORMAL);
    }

    @Test
    void mapsThePredictionBreakdownExactly() {
        PredictionOutput output = mapper.toOutput(result(List.of(), station("RTM")));

        PredictionBreakdownResponse prediction = output.prediction();
        assertThat(prediction.baseTravelTimeMinutes()).isEqualTo(90.0);
        assertThat(prediction.currentDelayMinutes()).isEqualTo(12);
        assertThat(prediction.predictedExtraDelayMinutes()).isEqualTo(8);
        assertThat(prediction.historicalAdjustmentMinutes()).isEqualTo(3);
        assertThat(prediction.recoveryMinutes()).isEqualTo(10);
        assertThat(prediction.predictedTotalDelayMinutes()).isEqualTo(23);
        assertThat(prediction.predictedEta()).isEqualTo(Instant.parse("2026-09-09T11:41:00Z"));
    }

    @Test
    void mapsTheHistoricalSourceAndProvenanceOntoTheBreakdown() {
        PredictionOutput output = mapper.toOutput(result(List.of(), station("RTM")));

        PredictionBreakdownResponse prediction = output.prediction();
        assertThat(prediction.historicalAdjustmentSource()).isEqualTo(HistoricalAdjustmentSource.SECTION);
        assertThat(prediction.historicalAdjustmentProvenance()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void filtersOutDisruptionsThatDidNotTrigger() {
        List<DisruptionResult> disruptions = List.of(
                new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 15, "rain triggered"),
                new DisruptionResult(DisruptionType.DENSE_FOG, false, 0, "fog did not trigger"));

        PredictionOutput output = mapper.toOutput(result(disruptions, station("RTM")));

        assertThat(output.simulation().triggeredDisruptions()).hasSize(1);
        assertThat(output.simulation().triggeredDisruptions().get(0).disruptionType()).isEqualTo(DisruptionType.HEAVY_RAIN);
        assertThat(output.simulation().triggeredDisruptions().get(0).disruptionDelayMinutes()).isEqualTo(15);
        assertThat(output.simulation().triggeredDisruptions().get(0).explanation()).isEqualTo("rain triggered");
    }

    @Test
    void mapsCascadeEffectsAndRecoveryIntoTheSimulationBlock() {
        PredictionOutput output = mapper.toOutput(result(List.of(), station("RTM")));

        assertThat(output.simulation().cascadeEffects()).hasSize(1);
        assertThat(output.simulation().cascadeEffects().get(0).depth()).isEqualTo(1);
        assertThat(output.simulation().cascadeEffects().get(0).additionalDelayMinutes()).isEqualTo(5);
        assertThat(output.simulation().recoveryMinutes()).isEqualTo(10);
    }

    @Test
    void mapsConfidenceFieldsExactly() {
        PredictionOutput output = mapper.toOutput(result(List.of(), station("RTM")));

        assertThat(output.confidence().score()).isEqualTo(75.0);
        assertThat(output.confidence().level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(output.confidence().contributingFactors()).containsExactly("speed data available");
        assertThat(output.confidence().warnings()).containsExactly("weather unavailable");
    }

    @Test
    void nullNextStationMapsToNullRatherThanAFabricatedStation() {
        PredictionOutput output = mapper.toOutput(result(List.of(), null));

        assertThat(output.nextStation()).isNull();
    }
}
