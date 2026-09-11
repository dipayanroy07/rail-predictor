package com.railpredictor.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.exception.PredictionNotApplicableException;
import com.railpredictor.exception.RailRadarUnavailableException;
import com.railpredictor.exception.TrainNotFoundException;
import com.railpredictor.model.domain.CascadeEffect;
import com.railpredictor.model.domain.ConfidenceScore;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.HistoricalAdjustmentResolution;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.dto.PredictionOutputMapper;
import com.railpredictor.model.enums.ConfidenceLevel;
import com.railpredictor.model.enums.DisruptionType;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.service.PredictionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the HTTP layer (controller + validation + {@code GlobalExceptionHandler}) with the
 * real {@link PredictionOutputMapper} but a mocked {@link PredictionService}, so these tests
 * prove the controller uses the existing service/mapper correctly without hitting any real
 * external provider.
 */
@WebMvcTest(PredictionController.class)
@Import(PredictionOutputMapper.class)
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PredictionService predictionService;

    private static PredictionResult validResult() {
        Station kota = new Station("KOTA", "Kota Jn", 25.18, 75.83);
        Station ratlam = new Station("RTM", "Ratlam Jn", 23.33, 75.04);
        return new PredictionResult(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, kota, ratlam,
                12, 465.0, 919.0, 92.5, SectionType.NORMAL,
                90.0, 8, 3, 10, 23,
                Instant.parse("2026-09-09T11:41:00Z"),
                new ConfidenceScore(75.0, ConfidenceLevel.MEDIUM, List.of("speed data available"), List.of()),
                List.of(new DisruptionResult(DisruptionType.HEAVY_RAIN, true, 18, "Weather reading reports heavy rain")),
                List.of(new CascadeEffect("Simulated: increased occupancy propagates delay", 1, 5)),
                List.of("No historical delay data available for this train/section - historical adjustment is 0."),
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR));
    }

    @Test
    void validTrainNumberReturns200WithTheMappedPredictionOutput() throws Exception {
        when(predictionService.getPrediction("12952")).thenReturn(validResult());

        mockMvc.perform(get("/api/v1/trains/12952/prediction"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trainNumber").value("12952"))
                .andExpect(jsonPath("$.trainName").value("Rajdhani Express"))
                .andExpect(jsonPath("$.currentStatus").value("RUNNING"))
                .andExpect(jsonPath("$.currentDelayMinutes").value(12))
                .andExpect(jsonPath("$.currentStation.code").value("KOTA"))
                .andExpect(jsonPath("$.nextStation.code").value("RTM"))
                .andExpect(jsonPath("$.distanceFromOriginKm").value(465.0))
                .andExpect(jsonPath("$.remainingDistanceKm").value(919.0))
                .andExpect(jsonPath("$.estimatedSpeedKmh").value(92.5))
                .andExpect(jsonPath("$.estimatedSectionCondition").value("NORMAL"))
                .andExpect(jsonPath("$.prediction.baseTravelTimeMinutes").value(90.0))
                .andExpect(jsonPath("$.prediction.predictedExtraDelayMinutes").value(8))
                .andExpect(jsonPath("$.prediction.historicalAdjustmentMinutes").value(3))
                .andExpect(jsonPath("$.prediction.recoveryMinutes").value(10))
                .andExpect(jsonPath("$.prediction.predictedTotalDelayMinutes").value(23))
                .andExpect(jsonPath("$.prediction.predictedEta").value("2026-09-09T11:41:00Z"))
                .andExpect(jsonPath("$.prediction.historicalAdjustmentSource").value("STATION_FALLBACK"))
                .andExpect(jsonPath("$.prediction.historicalAdjustmentProvenance").value("railradar"))
                .andExpect(jsonPath("$.simulation.triggeredDisruptions[0].disruptionType").value("HEAVY_RAIN"))
                .andExpect(jsonPath("$.simulation.triggeredDisruptions[0].disruptionDelayMinutes").value(18))
                .andExpect(jsonPath("$.simulation.cascadeEffects[0].depth").value(1))
                .andExpect(jsonPath("$.simulation.recoveryMinutes").value(10))
                .andExpect(jsonPath("$.confidence.score").value(75.0))
                .andExpect(jsonPath("$.confidence.level").value("MEDIUM"))
                .andExpect(jsonPath("$.warnings[0]").exists());

        verify(predictionService).getPrediction("12952");
    }

    @Test
    void nonNumericTrainNumberReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/trains/abcde/prediction"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_TRAIN_NUMBER"))
                .andExpect(jsonPath("$.path").value("/api/v1/trains/abcde/prediction"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(predictionService);
    }

    @Test
    void wrongLengthTrainNumberReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/trains/123/prediction"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TRAIN_NUMBER"));

        verifyNoInteractions(predictionService);
    }

    @Test
    void trainNotFoundReturns404WithTheExpectedErrorShape() throws Exception {
        when(predictionService.getPrediction("99999")).thenThrow(new TrainNotFoundException("99999"));

        mockMvc.perform(get("/api/v1/trains/99999/prediction"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("TRAIN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No train found for train number '99999'"))
                .andExpect(jsonPath("$.path").value("/api/v1/trains/99999/prediction"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void externalProviderFailureReturns503WithoutLeakingTheRawExceptionMessage() throws Exception {
        when(predictionService.getPrediction("12952"))
                .thenThrow(new RailRadarUnavailableException("connection reset by peer on host api.railradar.in"));

        mockMvc.perform(get("/api/v1/trains/12952/prediction"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("EXTERNAL_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("api.railradar.in"))));
    }

    @Test
    void malformedUpstreamDataReturns502() throws Exception {
        when(predictionService.getPrediction("12952"))
                .thenThrow(new MalformedRailRadarResponseException("missing field 'trainName'"));

        mockMvc.perform(get("/api/v1/trains/12952/prediction"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("INVALID_TRAIN_DATA"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("trainName"))));
    }

    @Test
    void predictionNotApplicableReturns422() throws Exception {
        when(predictionService.getPrediction("12952")).thenThrow(new PredictionNotApplicableException("12952"));

        mockMvc.perform(get("/api/v1/trains/12952/prediction"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PREDICTION_NOT_APPLICABLE"));
    }

    @Test
    void unexpectedErrorReturns500WithoutExposingItsMessage() throws Exception {
        when(predictionService.getPrediction("12952")).thenThrow(new RuntimeException("npe at line 42, oops"));

        mockMvc.perform(get("/api/v1/trains/12952/prediction"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("npe at line 42"))));
    }

    @Test
    void controllerCallsTheExistingPredictionServiceExactlyOnce() throws Exception {
        when(predictionService.getPrediction(eq("12952"))).thenReturn(validResult());

        mockMvc.perform(get("/api/v1/trains/12952/prediction")).andExpect(status().isOk());

        verify(predictionService).getPrediction("12952");
    }
}
