package com.railpredictor.historical;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.model.domain.HistoricalObservation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Pure statistics: turns a list of {@link HistoricalObservation}s for one (train, station) pair
 * into one {@link HistoricalDelayProfile}. Framework-free besides the injected {@link Clock} -
 * no JPA, no RailRadar DTOs, no repository access; see {@code HistoricalDelayProfileRefresher}
 * for where this is wired to persistence.
 *
 * <p><b>Sample unit</b> (see {@link HistoricalDelayProfile}'s own Javadoc and
 * docs/historical-data-design.md): one sample is one observation with a non-null
 * {@code arrivalDelayMinutes}. Observations lacking a delay figure are excluded from every
 * statistic <em>and</em> from {@code sampleCount} - they simply weren't usable, they are not
 * silently treated as zero.
 *
 * <p><b>Minimum sample policy</b>: none is enforced here. Zero usable observations produces a
 * profile with {@code sampleCount = 0} (matching {@code HistoricalDelay}'s own "no data"
 * convention) rather than being refused outright - deciding whether a given sample count is
 * large enough to actually <em>trust</em> for a prediction is {@code HistoricalDelayCalculator}'s
 * job (its own {@code minimumSampleCount}, unchanged by this phase), not this aggregator's. This
 * class's only job is to report the data honestly, however much or little of it exists.
 *
 * <p><b>Standard deviation</b>: sample standard deviation (Bessel's correction, {@code n-1}
 * denominator) - we are estimating variability of an ongoing process from a limited number of
 * observed journeys, the standard case for sample (not population) standard deviation. For
 * exactly one observation, {@code n-1 = 0} would make this undefined; this returns {@code 0.0}
 * for that case (an observed spread of zero for a single point) rather than {@code NaN}.
 *
 * <p><b>Median</b>: the standard convention - the middle value for an odd count, the average of
 * the two middle values for an even count.
 *
 * <p><b>Temporal cutoff (Phase 16H-7)</b>: {@link #aggregate(String, String, List, Instant)}
 * accepts a {@code referenceInstant} and includes only observations with
 * {@code !observedAt().isAfter(referenceInstant)} - the exact same inclusive-cutoff convention
 * {@code HistoricalSectionDelayProfileAggregator} has had since Phase 16F. Before this phase, this
 * class had no cutoff parameter at all, a documented asymmetry with the section-level aggregator
 * (see docs/historical-data-design.md's Phase 16H-5 "remaining limitations" and Phase 16H-7
 * notes). The 3-argument {@link #aggregate(String, String, List)} overload is preserved
 * unchanged for {@code HistoricalDelayProfileRefresher} (which always wants "as of right now") and
 * simply delegates with {@code Instant.now(clock)}.
 *
 * <p><b>This closes the aggregation-level gap, not the whole point-in-time story.</b> Unlike
 * {@code PostgresSectionHistoricalDelayProvider}, {@code PostgresHistoricalDelayProvider} still
 * reads only the single always-current materialized {@code historical_delay_profiles} row - it
 * does not (and this phase does not add) an on-demand re-aggregation path for a genuinely past
 * {@code referenceInstant}. A caller wanting a true point-in-time station-level profile must call
 * this aggregator directly against raw observations, exactly as
 * {@code PostgresSectionHistoricalDelayProvider} already does for sections - see
 * docs/historical-data-design.md's Phase 16H-7 notes for why extending the provider itself is left
 * for a future phase (it would touch {@code HistoricalDelayProvider}'s interface and its live
 * caller, {@code PredictionService}, which this phase deliberately does not touch).
 */
@Component
public class HistoricalDelayProfileAggregator {

    private final Clock clock;

    public HistoricalDelayProfileAggregator(Clock clock) {
        this.clock = clock;
    }

    /** As of right now - delegates to {@link #aggregate(String, String, List, Instant)} with
     * {@code Instant.now(clock)}. This is the overload {@code HistoricalDelayProfileRefresher}
     * uses; it never needs a cutoff other than "now". */
    public HistoricalDelayProfile aggregate(String trainNumber, String stationCode, List<HistoricalObservation> observations) {
        return aggregate(trainNumber, stationCode, observations, Instant.now(clock));
    }

    /**
     * @param referenceInstant the point-in-time cutoff: an observation is included only when
     *                          {@code !observation.observedAt().isAfter(referenceInstant)} - the
     *                          cutoff instant itself is inclusive, identical to
     *                          {@code HistoricalSectionDelayProfileAggregator}'s own convention.
     *                          No observation recorded strictly after this instant can ever
     *                          influence the returned profile.
     */
    public HistoricalDelayProfile aggregate(
            String trainNumber, String stationCode, List<HistoricalObservation> observations, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(stationCode, "stationCode");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        List<HistoricalObservation> withDelay = observations.stream()
                .filter(o -> !o.observedAt().isAfter(referenceInstant))
                .filter(o -> o.arrivalDelayMinutes() != null)
                .toList();

        Instant computedAt = Instant.now(clock);

        if (withDelay.isEmpty()) {
            return new HistoricalDelayProfile(trainNumber, stationCode, 0, 0.0, 0.0, 0.0, DataProvenance.UNAVAILABLE, computedAt);
        }

        List<Integer> delays = withDelay.stream().map(HistoricalObservation::arrivalDelayMinutes).sorted().toList();
        double average = delays.stream().mapToInt(Integer::intValue).average().orElseThrow();
        double median = median(delays);
        double standardDeviation = delays.size() == 1 ? 0.0 : sampleStandardDeviation(delays, average);
        String source = DataProvenance.combine(
                withDelay.stream().map(HistoricalObservation::source).collect(java.util.stream.Collectors.toCollection(TreeSet::new)));

        return new HistoricalDelayProfile(trainNumber, stationCode, delays.size(), average, median, standardDeviation, source, computedAt);
    }

    /** {@code sortedValues} must already be sorted ascending. */
    private static double median(List<Integer> sortedValues) {
        int size = sortedValues.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedValues.get(middle);
        }
        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
    }

    private static double sampleStandardDeviation(List<Integer> values, double mean) {
        double sumSquaredDifferences = values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum();
        return Math.sqrt(sumSquaredDifferences / (values.size() - 1));
    }
}
