package com.railpredictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.HistoricalAdjustmentProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RemainingRouteHistoricalStatus;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import com.railpredictor.model.domain.RouteCompleteness;
import java.util.List;
import org.junit.jupiter.api.Test;

class SectionHistoricalDelayCalculatorTest {

    private static RemainingRouteHistoricalSummary summaryWithTotal(Double total) {
        RemainingRouteHistoricalStatus status = total == null
                ? RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE
                : RemainingRouteHistoricalStatus.ALL_SECTIONS_AVAILABLE;
        int availableCount = total == null ? 0 : 1;
        String provenance = total == null ? DataProvenance.UNAVAILABLE : DataProvenance.RAILRADAR;
        return new RemainingRouteHistoricalSummary(
                "12952", RouteCompleteness.COMPLETE, List.of(), status, availableCount, total, provenance);
    }

    @Test
    void returnsNullWhenTheSummaryHasNoUsableSectionHistory() {
        SectionHistoricalDelayCalculator calculator =
                new SectionHistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.2, 5));

        assertThat(calculator.sectionAdjustmentMinutes(summaryWithTotal(null))).isNull();
    }

    @Test
    void appliesTheConfiguredWeightToThePositiveTotal() {
        SectionHistoricalDelayCalculator calculator =
                new SectionHistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.5, 5));

        assertThat(calculator.sectionAdjustmentMinutes(summaryWithTotal(10.0))).isEqualTo(5);
    }

    @Test
    void preservesANegativeRecoveryTotalRatherThanClampingToZero() {
        SectionHistoricalDelayCalculator calculator =
                new SectionHistoricalDelayCalculator(new HistoricalAdjustmentProperties(1.0, 5));

        assertThat(calculator.sectionAdjustmentMinutes(summaryWithTotal(-7.0))).isEqualTo(-7);
    }

    @Test
    void zeroTotalProducesZeroAdjustmentNotANullFallbackSignal() {
        SectionHistoricalDelayCalculator calculator =
                new SectionHistoricalDelayCalculator(new HistoricalAdjustmentProperties(0.5, 5));

        Integer adjustment = calculator.sectionAdjustmentMinutes(summaryWithTotal(0.0));

        assertThat(adjustment).isNotNull();
        assertThat(adjustment).isZero();
    }

    @Test
    void ignoresMinimumSampleCountEntirelySinceTheSectionProviderAlreadyEnforcesItsOwnThreshold() {
        // minimumSampleCount here is deliberately large - it must have zero effect, since the
        // section-level gate already happened upstream (Phase 16G) before AVAILABLE was decided.
        SectionHistoricalDelayCalculator calculator =
                new SectionHistoricalDelayCalculator(new HistoricalAdjustmentProperties(1.0, 1000));

        assertThat(calculator.sectionAdjustmentMinutes(summaryWithTotal(4.0))).isEqualTo(4);
    }
}
