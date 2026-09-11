package com.railpredictor.model.domain;

import java.time.Instant;

/**
 * Aggregated arrival-delay statistics for one train at one station, computed from
 * {@link HistoricalObservation} rows (see {@code HistoricalDelayProfileAggregator}) - never
 * fabricated from fewer than the observations actually available.
 *
 * <p><b>Sample unit (see docs/historical-data-design.md §Phase 16B):</b> one sample is one
 * completed station arrival with a known {@code arrivalDelayMinutes} - i.e. one
 * {@link HistoricalObservation} row for this (trainNumber, stationCode) with a non-null delay.
 * This is deliberately <em>station-level</em>, not section-level (a delay *change* between two
 * stations): the raw data available after Phase 16A cannot yet reliably support a section-level
 * statistic (see the design doc for why), so this profile answers "how delayed has this train
 * historically been by the time it reaches this station", not "how much extra delay does this
 * specific section tend to add".
 *
 * <p>{@code stationCode} is a plain station code, not a full {@link Station} - the persisted
 * aggregate only ever tracked the code (that's all its identity/grouping needs), and
 * reconstructing a display name from just a code would mean fabricating data this class has no
 * way to know.
 *
 * <p>{@code sampleCount} of 0 means no usable observation existed (not "zero delay observed") -
 * {@code averageArrivalDelayMinutes}/{@code medianArrivalDelayMinutes}/
 * {@code standardDeviationMinutes} are meaningless placeholders in that case, exactly like
 * {@link HistoricalDelay}'s own convention.
 *
 * <p>Unlike {@link HistoricalDelay}, the average/median here are <b>not</b> clamped to
 * non-negative - a train can historically run early at a station, and this profile reports that
 * raw fact. The adapter that turns this into a {@link HistoricalDelay} for
 * {@code HistoricalDelayProvider} callers clamps negative values to 0 there, not here - see
 * {@code PostgresHistoricalDelayProvider}.
 *
 * <p>{@code source} is a single provenance string when every contributing observation agrees, or
 * an explicit {@code DataProvenance.combine(...)} composite when they don't - never silently
 * collapsed to one source when several were actually mixed.
 */
public record HistoricalDelayProfile(
        String trainNumber,
        String stationCode,
        int sampleCount,
        double averageArrivalDelayMinutes,
        double medianArrivalDelayMinutes,
        double standardDeviationMinutes,
        String source,
        Instant computedAt) {

    public HistoricalDelayProfile {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        stationCode = Guard.requireNonBlank(stationCode, "stationCode");
        Guard.requireNonNegative(sampleCount, "sampleCount");
        Guard.requireNonNegative(standardDeviationMinutes, "standardDeviationMinutes");
        source = Guard.requireNonBlank(source, "source");
        computedAt = Guard.requireNonNull(computedAt, "computedAt");
    }
}
