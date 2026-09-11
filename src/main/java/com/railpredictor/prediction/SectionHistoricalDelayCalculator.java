package com.railpredictor.prediction;

import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link RemainingRouteHistoricalSummary} into a candidate section-level historical
 * adjustment, or {@code null} when the summary carries no usable section history at all - the
 * signal {@link PredictionEngine} uses to decide whether to fall back to the existing
 * station-level {@link HistoricalDelayCalculator} instead (see docs/prediction-model.md's Phase
 * 16H-2 notes on why there must be exactly one historical contribution, never both).
 *
 * <p>Deliberately reuses {@link HistoricalAdjustmentProperties#weight()} - the same one
 * configurable "how much do we trust/scale the historical signal" knob applies regardless of
 * which source (station-level average delay, or section-level average delay change) produced the
 * raw figure, so there remains exactly one weight to tune, not two independently-configurable
 * ones. {@link HistoricalAdjustmentProperties#minimumSampleCount()} is deliberately <b>not</b>
 * consulted here - the section-level minimum-sample gate
 * (`historical.section.minimum-sample-count`, Phase 16G) has already been enforced per section,
 * upstream, before a section ever counts as {@code AVAILABLE} and therefore before it can
 * contribute to {@link RemainingRouteHistoricalSummary#totalDelayChangeMinutes()} at all.
 *
 * <p>Never clamps, never converts a negative (recovery) total to zero, never averages the sections
 * again - {@code totalDelayChangeMinutes} is already the correct, final signed figure (see
 * {@link RemainingRouteHistoricalSummary}'s own Javadoc); this class only applies the one
 * configured weight to it.
 */
@Component
public class SectionHistoricalDelayCalculator {

    private final HistoricalAdjustmentProperties properties;

    public SectionHistoricalDelayCalculator(HistoricalAdjustmentProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the weighted section-level historical adjustment, or {@code null} when
     *         {@code summary} carries no usable section history
     *         ({@link RemainingRouteHistoricalSummary#totalDelayChangeMinutes()} is {@code null}) -
     *         a {@code null} return is the caller's signal to fall back to station-level history.
     */
    public Integer sectionAdjustmentMinutes(RemainingRouteHistoricalSummary summary) {
        Objects.requireNonNull(summary, "summary");
        Double total = summary.totalDelayChangeMinutes();
        if (total == null) {
            return null;
        }
        return (int) Math.round(total * properties.weight());
    }
}
