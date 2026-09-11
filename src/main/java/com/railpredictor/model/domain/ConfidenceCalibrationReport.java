package com.railpredictor.model.domain;

import java.util.List;

/**
 * Whether this system's confidence score actually correlates with real prediction reliability
 * (Phase 20) - "does {@code HIGH} confidence really mean lower error than {@code LOW}
 * confidence", checked against real evaluated outcomes, never assumed. Does not itself change the
 * confidence formula - see {@code ConfidenceCalibrationAuditor}'s own Javadoc.
 *
 * <p>{@code monotonic} is {@code true} only when every bucket with a sufficient sample shows
 * {@code meanAbsoluteError} non-increasing as confidence rises (HIGH &lt;= MEDIUM &lt;= LOW &lt;=
 * VERY_LOW) - buckets without a sufficient sample are excluded from this check entirely, never
 * assumed to fit the pattern.
 */
public record ConfidenceCalibrationReport(List<ConfidenceBucketMetrics> buckets, boolean monotonic, String explanation) {

    public ConfidenceCalibrationReport {
        buckets = List.copyOf(Guard.requireNonNull(buckets, "buckets"));
        explanation = Guard.requireNonBlank(explanation, "explanation");
    }
}
