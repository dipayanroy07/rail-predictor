package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code railway-disruption-impact.*} configuration keys (Phase 19) - every value here
 * is a <b>provisional engineering heuristic</b>, not an empirically-derived coefficient: this
 * codebase has no evaluated real-world disruption-to-delay outcomes to calibrate against yet (see
 * docs/prediction-model.md's Phase 19 notes on why {@code CalibrationStatus.INSUFFICIENT_DATA} is
 * the only value {@code HeuristicDisruptionImpactPolicy} can honestly report).
 *
 * <ul>
 *   <li>{@code engineeringBlockDefaultDelayMinutes} (minutes) - the flat delay assumed for an
 *       active {@code ENGINEERING_BLOCK} disruption, since no real source supplies a duration or
 *       severity figure to compute from. Heuristic.</li>
 *   <li>{@code signalFailureDefaultDelayMinutes} (minutes) - same role, for
 *       {@code SIGNAL_FAILURE}. Heuristic.</li>
 *   <li>{@code congestionDefaultDelayMinutes} (minutes) - same role, for {@code CONGESTION}
 *       ({@code severity}, if a source ever supplies it, is captured on {@code RailwayDisruption}
 *       for audit but is <b>not</b> currently used to scale this figure - a known, documented
 *       limitation, not an oversight). Heuristic.</li>
 *   <li>{@code maxSingleDisruptionDelayMinutes} (minutes) - an upper bound applied to <em>any</em>
 *       single disruption's estimated impact, including the physically-computed speed-restriction
 *       figure (see {@code HeuristicDisruptionImpactPolicy} - the physics itself isn't a heuristic,
 *       but bounding it against a pathological input is a deliberate safety measure, itself
 *       heuristic).</li>
 *   <li>{@code maxAggregateDelayMinutes} (minutes) - an upper bound on the <em>summed</em> impact
 *       across every currently-active disruption on one section (see
 *       {@code disruptionimpact.DisruptionImpactAggregator}). Heuristic.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "railway-disruption-impact")
public record DisruptionImpactProperties(
        int engineeringBlockDefaultDelayMinutes,
        int signalFailureDefaultDelayMinutes,
        int congestionDefaultDelayMinutes,
        int maxSingleDisruptionDelayMinutes,
        int maxAggregateDelayMinutes) {

    public DisruptionImpactProperties {
        requireNonNegative(engineeringBlockDefaultDelayMinutes, "engineering-block-default-delay-minutes");
        requireNonNegative(signalFailureDefaultDelayMinutes, "signal-failure-default-delay-minutes");
        requireNonNegative(congestionDefaultDelayMinutes, "congestion-default-delay-minutes");
        requireNonNegative(maxSingleDisruptionDelayMinutes, "max-single-disruption-delay-minutes");
        requireNonNegative(maxAggregateDelayMinutes, "max-aggregate-delay-minutes");
        if (maxAggregateDelayMinutes < maxSingleDisruptionDelayMinutes) {
            throw new IllegalArgumentException(
                    "railway-disruption-impact.max-aggregate-delay-minutes (" + maxAggregateDelayMinutes
                            + ") must not be less than max-single-disruption-delay-minutes (" + maxSingleDisruptionDelayMinutes + ")");
        }
    }

    private static void requireNonNegative(int value, String propertyName) {
        if (value < 0) {
            throw new IllegalArgumentException("railway-disruption-impact." + propertyName + " must not be negative: " + value);
        }
    }
}
