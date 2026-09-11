package com.railpredictor.model.domain;

/**
 * One named subgroup slice within a Phase 20 ablation comparison (e.g. "WITH_WEATHER" vs.
 * "WITHOUT_WEATHER") - wraps the existing {@link PredictionAccuracySlice} with an explicit
 * {@code sufficientSample} flag so a caller never has to separately remember to compare
 * {@code slice.sampleCount()} against {@code evaluation.calibration.minimum-sample-count} itself
 * before trusting the numbers.
 */
public record AblationVariantResult(String variantName, PredictionAccuracySlice slice, boolean sufficientSample) {

    public AblationVariantResult {
        variantName = Guard.requireNonBlank(variantName, "variantName");
        slice = Guard.requireNonNull(slice, "slice");
    }
}
