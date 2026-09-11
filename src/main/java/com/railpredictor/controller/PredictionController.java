package com.railpredictor.controller;

import com.railpredictor.model.domain.PredictionResult;
import com.railpredictor.model.dto.ErrorResponse;
import com.railpredictor.model.dto.PredictionOutput;
import com.railpredictor.model.dto.PredictionOutputMapper;
import com.railpredictor.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary only: validates the train number, calls {@link PredictionService}, maps the
 * result through {@link PredictionOutputMapper}. No prediction, simulation, RailRadar, weather,
 * historical, or confidence logic belongs here - see those packages instead. Errors are handled
 * centrally by {@code GlobalExceptionHandler}, not in this class.
 */
@RestController
@RequestMapping("/api/v1/trains")
@Validated
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionOutputMapper predictionOutputMapper;

    public PredictionController(PredictionService predictionService, PredictionOutputMapper predictionOutputMapper) {
        this.predictionService = predictionService;
        this.predictionOutputMapper = predictionOutputMapper;
    }

    @Operation(
            summary = "Get a live delay/ETA prediction for a train",
            description = "Fetches live train data, runs the disruption/cascade/recovery simulation, and returns "
                    + "a frontend-ready prediction. See docs/prediction-model.md for the ETA formula and "
                    + "docs/json-output.md for what each field represents.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prediction produced successfully",
                    content = @Content(schema = @Schema(implementation = PredictionOutput.class))),
            @ApiResponse(responseCode = "400", description = "Train number is not a valid 5-digit number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No train found for the given number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The train has no next station - nothing future to predict",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "The train data service returned invalid data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "The train data service is temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{trainNumber}/prediction")
    public PredictionOutput getPrediction(
            @Parameter(description = "5-digit train number", example = "12952")
            @PathVariable
            @Pattern(regexp = "\\d{5}", message = "trainNumber must be exactly 5 digits (e.g. 12952)")
            String trainNumber) {
        PredictionResult result = predictionService.getPrediction(trainNumber);
        return predictionOutputMapper.toOutput(result);
    }
}
