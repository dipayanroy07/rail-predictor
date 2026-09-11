package com.railpredictor.historical;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Pure statistics: turns every {@link HistoricalObservation} recorded for one train into a list
 * of {@link HistoricalSectionDelayProfile}s, one per (fromStationCode, toStationCode) adjacency it
 * actually finds in the raw data. Framework-free besides the injected {@link Clock} - no JPA, no
 * RailRadar DTOs, no repository access, no {@code PredictionEngine} dependency; see
 * {@code HistoricalSectionDelayProfileRefresher} for where this is wired to persistence.
 *
 * <p><b>Never fabricates adjacency.</b> A pair is only ever formed between two observations that:
 * <ol>
 *   <li>share the same (trainNumber, journeyDate) grouping - the strongest journey identity the
 *       current schema actually offers (see docs/historical-data-design.md's Phase 16E notes on
 *       why {@code journeyDate} is an observation-date, not a verified physical-journey
 *       identifier). Two observations under different journeyDate values are never pooled
 *       together, even if they plausibly belong to the same real overnight journey - if the raw
 *       data doesn't already agree they're the same journey, this aggregator does not guess;</li>
 *   <li>both have a non-null {@code stationSequence}; a null-sequence observation never
 *       participates in a pair, on either side;</li>
 *   <li>are adjacent once every non-null-sequence observation in that journey group is sorted by
 *       {@code stationSequence} ascending - never by station code, station name, database
 *       insertion order, or polling order. If two observations in the same journey group carry
 *       the exact same non-null sequence value, that value cannot be trusted to order them (a
 *       data anomaly the unique natural key on the raw table shouldn't normally allow, but this
 *       aggregator does not assume it can't happen) - the entire journey group is excluded
 *       from pairing rather than guessing which one comes first.</li>
 * </ol>
 * A "section" produced this way means <em>two consecutively-persisted observations</em>, not
 * necessarily one indivisible physical track segment - a sequence gap (a skipped/cancelled
 * station, or simply one this application never recorded) is never distinguished from any other
 * cause, and is never inferred or corrected; see {@link HistoricalSectionDelayProfile}'s own
 * Javadoc.
 *
 * <p><b>Sample formula</b>: for an adjacent pair FROM/TO, a *usable* delay-change sample requires
 * {@code FROM.departureDelayMinutes() != null && TO.arrivalDelayMinutes() != null}; the value is
 * {@code TO.arrivalDelayMinutes() - FROM.departureDelayMinutes()}, preserved unclamped (a negative
 * value is real - recovered time). An adjacency that is structurally found but never has both
 * delay figures present still produces a profile - with {@code sampleCount = 0} and
 * {@link DataProvenance#UNAVAILABLE} - because the adjacency itself is a real, discovered fact
 * even when no usable statistic follows from it; an adjacency that is never structurally found at
 * all produces no profile.
 *
 * <p><b>Temporal cutoff</b>: {@code referenceInstant} is compared against each observation's own
 * {@code observedAt} (never against the opaque, unparsed schedule/actual time strings - see
 * {@link HistoricalObservation}). An observation is in scope when
 * {@code !observation.observedAt().isAfter(referenceInstant)} - i.e. the cutoff instant itself is
 * <b>inclusive</b> ("the profile as it stood at or before this moment"), and anything recorded
 * strictly after is excluded. Callers wanting a full, unrestricted rebuild simply pass "now" (or
 * later) as {@code referenceInstant}.
 *
 * <p><b>Minimum sample policy</b>: none is enforced here, deliberately - see docs/historical-data-
 * design.md's Phase 16F notes on why a threshold is only introduced once an actual consumer needs
 * one (there is none yet; Phase 16F does not wire this into prediction).
 *
 * <p><b>Standard deviation</b>/<b>median</b>: identical conventions to
 * {@code HistoricalDelayProfileAggregator} - sample (Bessel-corrected, {@code n-1}) standard
 * deviation, {@code 0.0} for exactly one sample rather than {@code NaN}; median is the middle
 * value (odd count) or the average of the two middle values (even count).
 */
@Component
public class HistoricalSectionDelayProfileAggregator {

    private final Clock clock;

    public HistoricalSectionDelayProfileAggregator(Clock clock) {
        this.clock = clock;
    }

    public List<HistoricalSectionDelayProfile> aggregate(
            String trainNumber, List<HistoricalObservation> observations, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        List<HistoricalObservation> inScope = observations.stream()
                .filter(o -> o.trainNumber().equals(trainNumber))
                .filter(o -> !o.observedAt().isAfter(referenceInstant))
                .toList();

        Map<LocalDate, List<HistoricalObservation>> byJourney =
                inScope.stream().collect(Collectors.groupingBy(HistoricalObservation::journeyDate));

        Set<SectionKey> discoveredKeys = new LinkedHashSet<>();
        Map<SectionKey, List<Integer>> delayChangesByKey = new LinkedHashMap<>();
        Map<SectionKey, List<String>> sourcesByKey = new LinkedHashMap<>();

        for (List<HistoricalObservation> journeyObservations : byJourney.values()) {
            List<HistoricalObservation> ordered = orderedBySequenceOrNullIfAmbiguous(journeyObservations);
            if (ordered == null) {
                continue;
            }
            for (int i = 0; i + 1 < ordered.size(); i++) {
                HistoricalObservation from = ordered.get(i);
                HistoricalObservation to = ordered.get(i + 1);
                SectionKey key = new SectionKey(from.stationCode(), to.stationCode());
                discoveredKeys.add(key);
                if (from.departureDelayMinutes() != null && to.arrivalDelayMinutes() != null) {
                    int delayChange = to.arrivalDelayMinutes() - from.departureDelayMinutes();
                    delayChangesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(delayChange);
                    sourcesByKey.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(combineTwo(from.source(), to.source()));
                }
            }
        }

        Instant computedAt = Instant.now(clock);
        List<HistoricalSectionDelayProfile> profiles = new ArrayList<>();
        for (SectionKey key : discoveredKeys) {
            List<Integer> delayChanges = delayChangesByKey.getOrDefault(key, List.of());
            if (delayChanges.isEmpty()) {
                profiles.add(new HistoricalSectionDelayProfile(
                        trainNumber, key.fromStationCode(), key.toStationCode(),
                        0, 0.0, 0.0, 0.0, DataProvenance.UNAVAILABLE, computedAt));
                continue;
            }
            List<Integer> sorted = delayChanges.stream().sorted().toList();
            double average = sorted.stream().mapToInt(Integer::intValue).average().orElseThrow();
            double median = median(sorted);
            double standardDeviation = sorted.size() == 1 ? 0.0 : sampleStandardDeviation(sorted, average);
            String source = DataProvenance.combine(new TreeSet<>(sourcesByKey.get(key)));
            profiles.add(new HistoricalSectionDelayProfile(
                    trainNumber, key.fromStationCode(), key.toStationCode(),
                    sorted.size(), average, median, standardDeviation, source, computedAt));
        }
        return List.copyOf(profiles);
    }

    /**
     * Non-null-sequence observations for one journey, sorted ascending by {@code stationSequence}
     * - or {@code null} if two of them share the exact same sequence value, which makes their
     * relative order untrustworthy (see class Javadoc).
     */
    private static List<HistoricalObservation> orderedBySequenceOrNullIfAmbiguous(
            List<HistoricalObservation> journeyObservations) {
        List<HistoricalObservation> withSequence = journeyObservations.stream()
                .filter(o -> o.stationSequence() != null)
                .sorted(Comparator.comparingInt(HistoricalObservation::stationSequence))
                .toList();
        for (int i = 0; i + 1 < withSequence.size(); i++) {
            if (withSequence.get(i).stationSequence().equals(withSequence.get(i + 1).stationSequence())) {
                return null;
            }
        }
        return withSequence;
    }

    private static String combineTwo(String a, String b) {
        TreeSet<String> sources = new TreeSet<>();
        sources.add(a);
        sources.add(b);
        return DataProvenance.combine(sources);
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

    private record SectionKey(String fromStationCode, String toStationCode) {
    }
}
