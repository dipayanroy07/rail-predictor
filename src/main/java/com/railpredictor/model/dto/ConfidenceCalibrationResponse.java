package com.railpredictor.model.dto;

import java.util.List;

/** JSON shape of {@code ConfidenceCalibrationReport} (Phase 20). {@code monotonic} is only a
 * meaningful signal when at least two buckets have {@code sufficientSample=true} - see
 * {@code explanation} for the concrete reason whenever it isn't. */
public record ConfidenceCalibrationResponse(List<ConfidenceBucketResponse> buckets, boolean monotonic, String explanation) {
}
