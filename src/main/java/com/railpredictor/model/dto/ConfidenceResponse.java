package com.railpredictor.model.dto;

import com.railpredictor.model.enums.ConfidenceLevel;
import java.util.List;

/**
 * How reliable this prediction is likely to be, based on data/model quality - not a statistically
 * calibrated probability of accuracy. See {@code ConfidenceCalculator}.
 */
public record ConfidenceResponse(
        double score, ConfidenceLevel level, List<String> contributingFactors, List<String> warnings) {
}
