package com.railpredictor.model.domain;

import java.time.Instant;

/**
 * Aggregated <b>delay-change</b> statistics for one train across one section (a pair of
 * consecutively-persisted stations), computed from {@link HistoricalObservation} rows - see
 * {@code HistoricalSectionDelayProfileAggregator} - never fabricated from fewer than the section
 * samples actually available, and never computed by subtracting two already-aggregated
 * station-level {@link HistoricalDelayProfile} averages (which would silently average-then-subtract
 * instead of subtract-then-average - not equivalent, and would misrepresent variance).
 *
 * <p><b>Sample unit</b> (see docs/historical-data-design.md's Phase 16F notes): one sample is one
 * pair of consecutively-ordered {@link HistoricalObservation} rows, FROM and TO, that share the
 * same (trainNumber, journeyDate) grouping, both have a non-null, mutually distinct
 * {@code stationSequence} within that journey, and both carry the specific delay figure the
 * section formula needs:
 * <pre>{@code delayChangeMinutes = TO.arrivalDelayMinutes - FROM.departureDelayMinutes}</pre>
 * - the delay the train carried when it *left* FROM, compared to the delay it had when it
 * *reached* TO. Using FROM's arrival delay instead would misattribute dwell-time recovery at FROM
 * to this section itself, so the two are never mixed.
 *
 * <p><b>"Section" is a data-availability concept, not a guaranteed physical track segment.</b>
 * {@code fromStationCode}/{@code toStationCode} name two *consecutively-persisted* observations -
 * if an intermediate station was never observed (a sequence gap, for whatever reason: a genuinely
 * skipped/cancelled stop, or simply a station this application never recorded), this profile's
 * "section" silently spans whatever physical distance separates them. This is never guessed at or
 * corrected - the aggregator does not attempt to determine why a gap exists.
 *
 * <p>{@code fromStationCode}/{@code toStationCode} are plain codes, not full {@link Station}
 * values, for the same reason {@link HistoricalDelayProfile#stationCode()} is - the persisted
 * aggregate only ever tracked the codes.
 *
 * <p>{@code sampleCount} of 0 means the (fromStationCode, toStationCode) adjacency was observed
 * at least once, but never with both required delay figures present - not "zero delay change
 * observed". {@code averageDelayChangeMinutes}/{@code medianDelayChangeMinutes}/
 * {@code standardDeviationMinutes} are meaningless placeholders in that case, exactly like
 * {@link HistoricalDelayProfile}'s own "no data" convention.
 *
 * <p>Average/median are <b>never clamped</b> to non-negative - a negative
 * {@code delayChangeMinutes} means the train recovered time across this section, a genuine and
 * important fact this profile must preserve, not hide.
 *
 * <p>{@code source} is the combined provenance of every contributing section sample - each
 * sample's own source is itself the combination of its FROM and TO endpoints' sources (see
 * {@code DataProvenance#combine}), so a profile built from real-RailRadar-only samples reports
 * {@link DataProvenance#RAILRADAR}, and one built from any mix of sources reports an explicit
 * {@code mixed(...)} label - never silently collapsed to look like a single trusted source.
 */
public record HistoricalSectionDelayProfile(
        String trainNumber,
        String fromStationCode,
        String toStationCode,
        int sampleCount,
        double averageDelayChangeMinutes,
        double medianDelayChangeMinutes,
        double standardDeviationMinutes,
        String source,
        Instant computedAt) {

    public HistoricalSectionDelayProfile {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        fromStationCode = Guard.requireNonBlank(fromStationCode, "fromStationCode");
        toStationCode = Guard.requireNonBlank(toStationCode, "toStationCode");
        Guard.requireNonNegative(sampleCount, "sampleCount");
        Guard.requireNonNegative(standardDeviationMinutes, "standardDeviationMinutes");
        source = Guard.requireNonBlank(source, "source");
        computedAt = Guard.requireNonNull(computedAt, "computedAt");
    }
}
