package com.railpredictor.model.domain;

import com.railpredictor.model.enums.ConfidenceLevel;
import java.util.List;

/**
 * How reliable a prediction is likely to be, based on data/model quality - not a statistically
 * calibrated probability of accuracy. {@code contributingFactors} and {@code warnings} are
 * human-readable explanations (e.g. "speed data unavailable (-10)").
 */
public record ConfidenceScore(
        double score,
        ConfidenceLevel level,
        List<String> contributingFactors,
        List<String> warnings) {

    public ConfidenceScore {
        Guard.requireInRange(score, 0, 100, "score");
        level = Guard.requireNonNull(level, "level");
        contributingFactors = List.copyOf(Guard.requireNonNull(contributingFactors, "contributingFactors"));
        warnings = List.copyOf(Guard.requireNonNull(warnings, "warnings"));
    }
}
