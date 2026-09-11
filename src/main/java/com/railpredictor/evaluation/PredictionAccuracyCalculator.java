package com.railpredictor.evaluation;

import com.railpredictor.model.domain.PredictionAccuracyMetrics;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Turns a list of signed prediction errors into {@link PredictionAccuracyMetrics} (Phase 16H-5) -
 * pure statistics, no persistence, no knowledge of where the errors came from (station-level,
 * section-level, or a baseline - see {@code PredictionAccuracyReportBuilder}).
 *
 * <p>{@code error = predicted - actual} is expected to already be computed by the caller (see
 * {@link com.railpredictor.model.domain.PredictionSnapshot#errorMinutes()}) - this class only
 * aggregates already-signed values, it never computes or reinterprets the sign itself.
 */
@Component
public class PredictionAccuracyCalculator {

    public PredictionAccuracyMetrics compute(List<Integer> errors) {
        Objects.requireNonNull(errors, "errors");
        if (errors.isEmpty()) {
            return new PredictionAccuracyMetrics(0, 0.0, 0.0, 0.0);
        }

        double sumAbsoluteError = 0;
        double sumSquaredError = 0;
        double sumError = 0;
        for (int error : errors) {
            sumAbsoluteError += Math.abs(error);
            sumSquaredError += (double) error * error;
            sumError += error;
        }
        int sampleCount = errors.size();
        double mae = sumAbsoluteError / sampleCount;
        double rmse = Math.sqrt(sumSquaredError / sampleCount);
        double bias = sumError / sampleCount;
        return new PredictionAccuracyMetrics(sampleCount, mae, rmse, bias);
    }
}
