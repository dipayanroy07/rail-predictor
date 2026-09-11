package com.railpredictor.controller;

import com.railpredictor.evaluation.CalibrationEvaluationService;
import com.railpredictor.evaluation.PredictionAccuracyReportService;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionAccuracyReportFilter;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.dto.AccuracyReportMapper;
import com.railpredictor.model.dto.AccuracyReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only prediction-accuracy reporting (Phase 16H-6) - observes {@code PredictionSnapshot}s
 * already recorded and evaluated by the Phase 16H-5 foundation; never triggers evaluation itself
 * and never affects live prediction (see {@code PredictionAccuracyReportService}).
 */
@RestController
@RequestMapping("/api/v1/evaluation")
@Validated
public class EvaluationController {

    private final PredictionAccuracyReportService reportService;
    private final CalibrationEvaluationService calibrationEvaluationService;
    private final AccuracyReportMapper mapper;

    public EvaluationController(
            PredictionAccuracyReportService reportService,
            CalibrationEvaluationService calibrationEvaluationService,
            AccuracyReportMapper mapper) {
        this.reportService = reportService;
        this.calibrationEvaluationService = calibrationEvaluationService;
        this.mapper = mapper;
    }

    @Operation(
            summary = "Prediction accuracy report",
            description = "Aggregate MAE/RMSE/bias for the model vs. a current-delay-only baseline, "
                    + "computed only from already-evaluated prediction snapshots. Returns an explicit "
                    + "evaluationEnabled=false body (not an error, not a fabricated empty dataset) when "
                    + "prediction.evaluation.enabled=false.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Accuracy report, or an explicit evaluation-disabled body"),
        @ApiResponse(responseCode = "400", description = "An invalid or unsatisfiable filter parameter")
    })
    @GetMapping("/accuracy")
    public AccuracyReportResponse getAccuracyReport(
            @Parameter(description = "Restrict to this train number (exactly 5 digits)")
                    @RequestParam(required = false)
                    @Pattern(regexp = "\\d{5}", message = "trainNumber must be exactly 5 digits (e.g. 12952)")
                    String trainNumber,
            @Parameter(description = "Restrict to this target station code") @RequestParam(required = false)
                    String stationCode,
            @Parameter(description = "Only predictions made at or after this instant (ISO-8601)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant predictionMadeFrom,
            @Parameter(description = "Only predictions made at or before this instant (ISO-8601)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant predictionMadeTo,
            @Parameter(description = "Restrict to this historical-adjustment source")
                    @RequestParam(required = false)
                    HistoricalAdjustmentSource historicalAdjustmentSource,
            @Parameter(description = "Restrict to this evaluation status")
                    @RequestParam(required = false)
                    PredictionEvaluationStatus evaluationStatus) {
        PredictionAccuracyReportFilter filter = new PredictionAccuracyReportFilter(
                trainNumber, stationCode, predictionMadeFrom, predictionMadeTo,
                historicalAdjustmentSource, evaluationStatus);

        return reportService.getReport(filter)
                .map(report -> mapper.toEnabledResponse(report, calibrationEvaluationService.getReport().orElse(null)))
                .orElseGet(mapper::toDisabledResponse);
    }
}
