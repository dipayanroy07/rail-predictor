package com.railpredictor.prediction;

import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.model.domain.HistoricalDelay;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Turns a train/section's historical average delay into a small prediction adjustment, weighted
 * by a configurable factor rather than a blindly hardcoded percentage.
 *
 * <p>Returns {@code null} (Phase 16H-3), not {@code 0}, when {@code sampleCount} is below the
 * configured minimum - not enough history to trust the average. Mirrors
 * {@link SectionHistoricalDelayCalculator#sectionAdjustmentMinutes}'s exact
 * usable-or-{@code null} contract, so {@code PredictionEngine} can tell "the station-level
 * fallback is usable" apart from "the station-level average genuinely happens to be zero" -
 * collapsing those into the same {@code 0} would make it impossible to correctly report
 * {@code HistoricalAdjustmentSource.NONE} versus {@code STATION_FALLBACK}.
 */
@Component
public class HistoricalDelayCalculator {

    private final HistoricalAdjustmentProperties properties;

    public HistoricalDelayCalculator(HistoricalAdjustmentProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the weighted station-level historical adjustment, or {@code null} when
     *         {@code historicalDelay.sampleCount()} is below the configured minimum - a
     *         {@code null} return is the caller's signal that station-level history is not a
     *         usable fallback either.
     */
    public Integer historicalAdjustmentMinutes(HistoricalDelay historicalDelay) {
        Objects.requireNonNull(historicalDelay, "historicalDelay");
        if (historicalDelay.sampleCount() < properties.minimumSampleCount()) {
            return null;
        }
        return (int) Math.round(historicalDelay.averageDelayMinutes() * properties.weight());
    }
}
