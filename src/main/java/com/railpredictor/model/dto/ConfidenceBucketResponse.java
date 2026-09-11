package com.railpredictor.model.dto;

import com.railpredictor.model.enums.ConfidenceLevel;

/** JSON shape of {@code ConfidenceBucketMetrics} (Phase 20). */
public record ConfidenceBucketResponse(
        ConfidenceLevel level,
        int sampleCount,
        boolean sufficientSample,
        double meanAbsoluteError,
        double medianAbsoluteError,
        double p90AbsoluteError) {
}
