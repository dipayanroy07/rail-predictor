package com.railpredictor.historical;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RemainingRoute;
import com.railpredictor.model.domain.RemainingRouteHistoricalStatus;
import com.railpredictor.model.domain.RemainingRouteHistoricalSummary;
import com.railpredictor.model.domain.RemainingRouteSection;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Queries {@link HistoricalSectionDelayProvider} once per remaining section of a train's
 * {@link RemainingRoute} and combines the results into one
 * {@link RemainingRouteHistoricalSummary} - the prerequisite Phase 16H-1 builds so a future phase
 * (16H-2) can decide how prediction should actually consume it. Deliberately <b>not</b> called
 * from {@code PredictionEngine} or {@code PredictionService} yet, and deliberately does not touch
 * the existing station-level {@code HistoricalDelayProvider}/{@code HistoricalDelayCalculator} -
 * see this class's own package/module boundary and docs/historical-data-design.md's Phase 16H-1
 * notes on why there must ultimately be exactly one historical contribution to a prediction, not
 * two independently-additive ones.
 *
 * <p>Never substitutes station-level historical data for a section with no usable history - a
 * section that is {@code INSUFFICIENT_SAMPLES} or {@code NOT_FOUND} simply does not contribute to
 * {@link RemainingRouteHistoricalSummary#totalDelayChangeMinutes()}; its own result is still
 * preserved in {@link RemainingRouteHistoricalSummary#sectionResults()} for full visibility.
 */
@Component
public class RemainingRouteHistoricalAggregator {

    private final HistoricalSectionDelayProvider sectionDelayProvider;

    public RemainingRouteHistoricalAggregator(HistoricalSectionDelayProvider sectionDelayProvider) {
        this.sectionDelayProvider = sectionDelayProvider;
    }

    public RemainingRouteHistoricalSummary summarize(String trainNumber, RemainingRoute route, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        if (route.sections().isEmpty()) {
            return new RemainingRouteHistoricalSummary(
                    trainNumber, route.completeness(), List.of(),
                    RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS, 0, null, DataProvenance.UNAVAILABLE);
        }

        List<SectionHistoricalDelayResult> results = new ArrayList<>();
        for (RemainingRouteSection remainingSection : route.sections()) {
            RouteSection section = remainingSection.section();
            results.add(sectionDelayProvider.getSectionDelay(trainNumber, section, referenceInstant));
        }

        List<SectionHistoricalDelayResult> available = results.stream()
                .filter(r -> r.status() == SectionHistoricalDelayStatus.AVAILABLE)
                .toList();

        if (available.isEmpty()) {
            return new RemainingRouteHistoricalSummary(
                    trainNumber, route.completeness(), List.copyOf(results),
                    RemainingRouteHistoricalStatus.NO_SECTIONS_AVAILABLE, 0, null, DataProvenance.UNAVAILABLE);
        }

        double totalDelayChangeMinutes = available.stream()
                .mapToDouble(r -> r.profile().averageDelayChangeMinutes())
                .sum();
        String provenance = DataProvenance.combine(
                new TreeSet<>(available.stream().map(r -> r.profile().source()).toList()));

        RemainingRouteHistoricalStatus status = available.size() == results.size()
                ? RemainingRouteHistoricalStatus.ALL_SECTIONS_AVAILABLE
                : RemainingRouteHistoricalStatus.PARTIAL_SECTIONS_AVAILABLE;

        return new RemainingRouteHistoricalSummary(
                trainNumber, route.completeness(), List.copyOf(results),
                status, available.size(), totalDelayChangeMinutes, provenance);
    }
}
