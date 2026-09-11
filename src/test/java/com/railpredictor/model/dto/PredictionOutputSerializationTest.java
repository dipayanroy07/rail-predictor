package com.railpredictor.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the JSON shape Jackson actually produces for {@link PredictionOutput} - field names,
 * ISO-8601 dates, and round-trip deserialization. */
class PredictionOutputSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static PredictionOutput sample() {
        return new PredictionOutput(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, 12,
                new StationResponse("KOTA", "Kota Jn", 25.18, 75.83),
                new StationResponse("RTM", "Ratlam Jn", null, null),
                465.0, 919.0, 92.5, SectionType.NORMAL,
                new PredictionBreakdownResponse(90.0, 12, 8, 3, HistoricalAdjustmentSource.STATION_FALLBACK,
                        "test-provider", 10, 23, Instant.parse("2026-09-09T11:41:00Z")),
                new SimulationResponse(
                        List.of(new DisruptionResponse(DisruptionType.HEAVY_RAIN, 15, "Weather reading reports heavy rain")),
                        List.of(new CascadeEffectResponse("Simulated: increased occupancy propagates delay", 1, 5)),
                        10),
                new ConfidenceResponse(75.0, ConfidenceLevel.MEDIUM, List.of("speed data available"), List.of()),
                List.of("No historical delay data available for this train/section - historical adjustment is 0."));
    }

    @Test
    void serializesDatesAsIso8601StringsNotEpochTimestamps() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json).contains("\"predictedEta\" : \"2026-09-09T11:41:00Z\"");
    }

    @Test
    void serializesEnumsAsTheirName() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json).contains("\"currentStatus\" : \"RUNNING\"");
        assertThat(json).contains("\"estimatedSectionCondition\" : \"NORMAL\"");
        assertThat(json).contains("\"disruptionType\" : \"HEAVY_RAIN\"");
        assertThat(json).contains("\"level\" : \"MEDIUM\"");
        assertThat(json).contains("\"historicalAdjustmentSource\" : \"STATION_FALLBACK\"");
    }

    @Test
    void containsTheHistoricalSourceAndProvenanceFields() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json)
                .contains("\"historicalAdjustmentSource\" : \"STATION_FALLBACK\"")
                .contains("\"historicalAdjustmentProvenance\" : \"test-provider\"");
    }

    @Test
    void unknownStationCoordinatesSerializeAsExplicitNullNotAMissingField() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json).contains("\"latitude\" : null");
        assertThat(json).contains("\"longitude\" : null");
    }

    @Test
    void containsEveryTopLevelSectionRequestedForThisPhase() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json)
                .contains("\"trainNumber\"")
                .contains("\"trainName\"")
                .contains("\"currentStatus\"")
                .contains("\"currentDelayMinutes\"")
                .contains("\"currentStation\"")
                .contains("\"nextStation\"")
                .contains("\"distanceFromOriginKm\"")
                .contains("\"remainingDistanceKm\"")
                .contains("\"estimatedSpeedKmh\"")
                .contains("\"estimatedSectionCondition\"")
                .contains("\"prediction\"")
                .contains("\"simulation\"")
                .contains("\"confidence\"")
                .contains("\"warnings\"");
    }

    @Test
    void roundTripsThroughDeserializationUnchanged() throws Exception {
        PredictionOutput original = sample();

        String json = objectMapper.writeValueAsString(original);
        PredictionOutput deserialized = objectMapper.readValue(json, PredictionOutput.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void confidenceScoreDomainObjectAlsoSerializesCleanly() throws Exception {
        // Sanity check that the domain ConfidenceScore itself (reused inside the pipeline,
        // though never returned directly by the API) is Jackson-serializable too.
        ConfidenceScore score = new ConfidenceScore(50.0, ConfidenceLevel.LOW, List.of(), List.of("no data"));

        String json = objectMapper.writeValueAsString(score);

        assertThat(json).contains("\"level\" : \"LOW\"");
    }
}
