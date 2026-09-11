package com.railpredictor.evaluation;

import com.railpredictor.model.domain.ErrorDistribution;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Turns a list of signed prediction errors into an {@link ErrorDistribution} (median and P90 of
 * the absolute error) - Phase 20's small addition alongside {@link PredictionAccuracyCalculator}'s
 * existing mean-based MAE/RMSE/bias. Pure, stateless, no persistence - mirrors
 * {@code PredictionAccuracyCalculator}'s own shape exactly.
 *
 * <p><b>P90</b> here is the simplest defensible definition for a finite sample: sort the absolute
 * errors ascending and take the value at index {@code ceil(0.9 * n) - 1} (the "nearest-rank"
 * method) - not an interpolated percentile, since interpolation would imply a precision this
 * phase's own instructions warn against manufacturing.
 */
@Component
public class ErrorDistributionCalculator {

    public ErrorDistribution compute(List<Integer> errors) {
        Objects.requireNonNull(errors, "errors");
        if (errors.isEmpty()) {
            return new ErrorDistribution(0, 0.0, 0.0);
        }

        List<Integer> sortedAbsolute = errors.stream().map(Math::abs).sorted().toList();
        int n = sortedAbsolute.size();

        double median = n % 2 == 1
                ? sortedAbsolute.get(n / 2)
                : (sortedAbsolute.get(n / 2 - 1) + sortedAbsolute.get(n / 2)) / 2.0;

        int p90Index = Math.min(n - 1, (int) Math.ceil(0.9 * n) - 1);
        double p90 = sortedAbsolute.get(Math.max(0, p90Index));

        return new ErrorDistribution(n, median, p90);
    }
}
