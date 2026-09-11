package com.railpredictor.model.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One raw, real-world fact: a train's recorded arrival and/or departure at one station on one
 * journey. This is the raw-observation layer feeding the aggregated {@link HistoricalDelayProfile}
 * (Phase 16B, via {@code HistoricalDelayProfileAggregator}) - it is not itself a statistic, and
 * no aggregation happens here.
 *
 * <p><b>Arrival and departure are always kept as separate, independently-nullable events - never
 * merged into one generic "delay" field.</b> A station may genuinely supply only one of the two
 * (an origin station has a departure but never an arrival; a currently-halted station may have an
 * arrival but no departure yet; a station the train hasn't reached has neither and produces no
 * observation at all - see {@code HistoricalObservationMapper}), or both. Nothing here fabricates
 * a missing event or its delay to make a row "complete."
 *
 * <p>{@code scheduledArrival}/{@code actualArrival}/{@code scheduledDeparture}/
 * {@code actualDeparture} are kept as RailRadar's own raw strings rather than parsed into a
 * structured time type: their exact format/timezone hasn't been confirmed against a real
 * response (no API key was available to verify - see docs/historical-data-design.md), and
 * inventing a parsing scheme for an unconfirmed format would risk silently wrong data.
 * {@code arrivalDelayMinutes}/{@code departureDelayMinutes} come directly from RailRadar's own
 * pre-computed delay figures (not derived here), so they may be negative (running early) and are
 * left unclamped - this is a raw fact, not a display value.
 *
 * <p><b>Future section-level use (design only - see docs/historical-data-design.md's Phase 16D
 * notes; not implemented by this raw layer):</b> a section sample between two consecutive stops
 * FROM and TO must be computed as {@code TO.arrivalDelayMinutes - FROM.departureDelayMinutes} -
 * the delay the train carried when it *left* FROM, not when it *arrived* at FROM (using arrival
 * delay at FROM would misattribute dwell-time recovery at FROM to the FROM→TO section itself).
 * This record only preserves the raw fields such a computation would need; no aggregation of any
 * kind happens here.
 *
 * <p>{@code observedAt} means "when the application observed/persisted this external fact" (via
 * the injected {@code Clock} at mapping time) - it is <b>not</b> "when the railway event actually
 * occurred" (that would require parsing the opaque time strings above, which isn't done). It may
 * later serve as an approximate point-in-time cutoff for temporal-leakage-safe historical queries
 * (see docs/historical-data-design.md), bounded by polling latency rather than exact.
 *
 * <p>{@code journeyDate} is an <b>observation-date</b>, not a verified physical-journey
 * identifier - see {@code HistoricalJourneyDateProperties}'s Javadoc for why RailRadar supplies no
 * trustworthy journey-start-date field, and for the "operating day" mitigation this app applies to
 * reduce (not eliminate) midnight-crossing journey fragmentation.
 *
 * <p>{@code source} distinguishes a real RailRadar observation ({@link DataProvenance#RAILRADAR})
 * from mock/unavailable/assumed data - see {@link DataProvenance}.
 */
public record HistoricalObservation(
        String trainNumber,
        LocalDate journeyDate,
        String stationCode,
        Integer stationSequence,
        String scheduledArrival,
        String actualArrival,
        String scheduledDeparture,
        String actualDeparture,
        Integer arrivalDelayMinutes,
        Integer departureDelayMinutes,
        Instant observedAt,
        String source) {

    public HistoricalObservation {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        journeyDate = Guard.requireNonNull(journeyDate, "journeyDate");
        stationCode = Guard.requireNonBlank(stationCode, "stationCode");
        observedAt = Guard.requireNonNull(observedAt, "observedAt");
        source = Guard.requireNonBlank(source, "source");
    }
}
