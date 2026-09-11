package com.railpredictor.model.dto;

/** JSON shape of {@code AblationVariantResult} (Phase 20). {@code sufficientSample} is
 * {@code false} when {@code slice.sampleCount()} is below {@code evaluation.calibration.minimum-
 * sample-count} - the metrics are still shown, but a client must not treat them as reliable. */
public record AblationVariantResponse(String variantName, AccuracySliceResponse slice, boolean sufficientSample) {
}
