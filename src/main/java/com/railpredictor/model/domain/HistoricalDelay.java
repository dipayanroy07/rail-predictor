package com.railpredictor.model.domain;

import java.time.DayOfWeek;
import java.time.Month;

/**
 * Aggregated historical delay statistics for a train on a route section, for the matched
 * day-of-week/month/time-period bucket. {@code sampleCount} of 0 means no historical data was
 * available; callers should treat the other stats as meaningless in that case.
 *
 * <p>{@code source} names where these statistics actually came from (e.g. {@link
 * DataProvenance#MOCK}), so mock data is never mistaken for a real historical record - mirrors
 * {@link WeatherData#source()}.
 */
public record HistoricalDelay(
        String trainNumber,
        RouteSection section,
        DayOfWeek dayOfWeek,
        Month month,
        String timePeriod,
        double averageDelayMinutes,
        double medianDelayMinutes,
        double standardDeviationMinutes,
        int sampleCount,
        String source) {

    public HistoricalDelay {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        section = Guard.requireNonNull(section, "section");
        dayOfWeek = Guard.requireNonNull(dayOfWeek, "dayOfWeek");
        month = Guard.requireNonNull(month, "month");
        Guard.requireNonNegative(averageDelayMinutes, "averageDelayMinutes");
        Guard.requireNonNegative(medianDelayMinutes, "medianDelayMinutes");
        Guard.requireNonNegative(standardDeviationMinutes, "standardDeviationMinutes");
        Guard.requireNonNegative(sampleCount, "sampleCount");
        source = Guard.requireNonBlank(source, "source");
    }
}
