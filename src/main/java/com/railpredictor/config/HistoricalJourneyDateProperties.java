package com.railpredictor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls how {@code HistoricalObservationMapper} assigns a {@code journeyDate} to each raw
 * observation - see docs/historical-data-design.md's Phase 16E "journey identity" section for the
 * full reasoning. RailRadar's live-status response carries no trustworthy journey-start-date or
 * journey-identifier field (verified by inspection: {@code LiveTrainStatusData}, {@code RouteStop},
 * {@code CurrentLocation}, {@code NextHalt}, and {@code ResponseMeta} carry no such field; the only
 * date-adjacent fields, {@code lastUpdatedAt} and {@code meta.timestamp}, describe when the
 * response was generated, not when the journey started, and are themselves opaque, unconfirmed-
 * format strings). So {@code journeyDate} necessarily remains an <b>observation-date</b> - the
 * calendar date, per the application's own clock, on which a given station was recorded - not a
 * verified physical-journey identifier.
 *
 * <p>{@code operatingDayStartHour} (0-23, default 0 = exact legacy behaviour, no change) lets an
 * operator who knows a specific overnight service's schedule reduce - not eliminate - the risk of
 * one physical journey being split across two {@code journeyDate} values purely because polling
 * happened to straddle local midnight: an observation recorded before this hour is attributed to
 * the previous calendar day instead of the literal current date. This is the same "operating day"
 * convention transit systems commonly use (e.g. GTFS's own allowance for times past 24:00:00 to
 * still mean the previous service day) - it uses only the application's own trusted clock, never
 * RailRadar data, so it invents nothing about the journey itself. It does not solve a journey that
 * genuinely spans more than one operating day boundary (a very long-haul train) - that remains a
 * documented, accepted limitation.
 */
@ConfigurationProperties(prefix = "historical.journey-date")
public record HistoricalJourneyDateProperties(int operatingDayStartHour) {

    public HistoricalJourneyDateProperties {
        if (operatingDayStartHour < 0 || operatingDayStartHour > 23) {
            throw new IllegalArgumentException(
                    "historical.journey-date.operating-day-start-hour must be between 0 and 23: "
                            + operatingDayStartHour);
        }
    }
}
