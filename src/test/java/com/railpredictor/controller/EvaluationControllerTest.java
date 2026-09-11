package com.railpredictor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.railpredictor.evaluation.CalibrationEvaluationService;
import com.railpredictor.evaluation.PredictionAccuracyReportBuilder;
import com.railpredictor.evaluation.PredictionAccuracyCalculator;
import com.railpredictor.evaluation.PredictionAccuracyReportService;
import com.railpredictor.exception.InvalidAccuracyFilterException;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyReport;
import com.railpredictor.model.domain.PredictionAccuracyReportFilter;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import com.railpredictor.model.dto.AccuracyReportMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the HTTP layer (controller + validation + {@code GlobalExceptionHandler}) with the
 * real {@link AccuracyReportMapper} but a mocked {@link PredictionAccuracyReportService}, mirroring
 * {@code PredictionControllerTest}'s own established pattern.
 */
@WebMvcTest(EvaluationController.class)
@Import(AccuracyReportMapper.class)
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PredictionAccuracyReportService reportService;

    @MockitoBean
    private CalibrationEvaluationService calibrationEvaluationService;

    private static PredictionAccuracyReport sampleReport() {
        PredictionSnapshot snapshot = new PredictionSnapshot(
                1L, "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
        PredictionAccuracyReportBuilder builder = new PredictionAccuracyReportBuilder(new PredictionAccuracyCalculator());
        return builder.buildReport(List.of(snapshot));
    }

    @Test
    void successfulReportReturns200WithTheExpectedShape() throws Exception {
        when(reportService.getReport(any())).thenReturn(Optional.of(sampleReport()));

        mockMvc.perform(get("/api/v1/evaluation/accuracy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationEnabled").value(true))
                .andExpect(jsonPath("$.overall.sampleCount").value(1))
                .andExpect(jsonPath("$.overall.exactCount").value(1))
                .andExpect(jsonPath("$.overall.approximateCount").value(0))
                .andExpect(jsonPath("$.overall.comparison.currentModel.bias").value(2.0))
                .andExpect(jsonPath("$.exactOnly.currentModel.sampleCount").value(1))
                .andExpect(jsonPath("$.bySource.STATION_FALLBACK.sampleCount").value(1))
                .andExpect(jsonPath("$.byProvenance.railradar.sampleCount").value(1));
    }

    @Test
    void evaluationDisabledReturns200WithAnExplicitDisabledBody() throws Exception {
        when(reportService.getReport(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/evaluation/accuracy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationEnabled").value(false))
                .andExpect(jsonPath("$.overall").doesNotExist());
    }

    @Test
    void invalidTrainNumberFilterReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/evaluation/accuracy").param("trainNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(reportService);
    }

    @Test
    void invalidEnumFilterValueReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/evaluation/accuracy").param("historicalAdjustmentSource", "NOT_A_SOURCE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FILTER"));

        verifyNoInteractions(reportService);
    }

    @Test
    void invertedTimeRangeReturns400() throws Exception {
        when(reportService.getReport(any()))
                .thenThrow(new InvalidAccuracyFilterException("predictionMadeFrom must not be after predictionMadeTo"));

        mockMvc.perform(get("/api/v1/evaluation/accuracy")
                        .param("predictionMadeFrom", "2026-06-01T00:00:00Z")
                        .param("predictionMadeTo", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FILTER"));
    }

    @Test
    void repositoryFailureReturns500WithoutLeakingItsMessage() throws Exception {
        when(reportService.getReport(any())).thenThrow(new RuntimeException("connection pool exhausted"));

        mockMvc.perform(get("/api/v1/evaluation/accuracy"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("connection pool exhausted"))));
    }

    @Test
    void filterParametersAreForwardedToTheService() throws Exception {
        when(reportService.getReport(any())).thenReturn(Optional.of(sampleReport()));

        mockMvc.perform(get("/api/v1/evaluation/accuracy")
                        .param("trainNumber", "12952")
                        .param("stationCode", "KOTA")
                        .param("historicalAdjustmentSource", "STATION_FALLBACK")
                        .param("evaluationStatus", "EVALUATED_EXACT"))
                .andExpect(status().isOk());

        verify(reportService).getReport(new PredictionAccuracyReportFilter(
                "12952", "KOTA", null, null,
                HistoricalAdjustmentSource.STATION_FALLBACK, PredictionEvaluationStatus.EVALUATED_EXACT));
    }
}
