package com.railpredictor.railradar.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.HistoricalJourneyDateProperties;
import com.railpredictor.config.HistoricalObservationValidationProperties;
import com.railpredictor.historical.HistoricalDataMetrics;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.railradar.dto.CurrentLocation;
import com.railpredictor.railradar.dto.LiveTrainStatusData;
import com.railpredictor.railradar.dto.NextHalt;
import com.railpredictor.railradar.dto.RouteStop;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalObservationMapperTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-09T15:00:00Z"), ZoneOffset.UTC);

    private final HistoricalDataMetrics metrics = new HistoricalDataMetrics();
    private final HistoricalObservationMapper mapper = mapperWithDayStartHour(0);

    private HistoricalObservationMapper mapperWithDayStartHour(int dayStartHour) {
        return new HistoricalObservationMapper(
                FIXED_CLOCK,
                new HistoricalObservationValidationProperties(4320),
                new HistoricalJourneyDateProperties(dayStartHour),
                metrics);
    }

    private static RouteStop stop(String code, Integer sequence, String actualArrival) {
        return stop(code, sequence, actualArrival, 12, 12);
    }

    private static RouteStop stop(String code, Integer sequence, String actualArrival, Integer arrivalDelay, Integer departureDelay) {
        return new RouteStop(sequence, code, code + " Station", true, 0.0, 0.0,
                "20:39", "20:41", actualArrival, actualArrival == null ? null : "20:53",
                arrivalDelay, departureDelay, "departed", 465.0, null, "1");
    }

    /** An origin-shaped stop: departure known, no arrival ever recorded (a train doesn't "arrive"
     * at its own origin). */
    private static RouteStop originStop(String code, Integer sequence, String actualDeparture, Integer departureDelay) {
        return new RouteStop(sequence, code, code + " Station", true, 0.0, 0.0,
                null, "08:00", null, actualDeparture,
                null, departureDelay, "departed", 0.0, null, "1");
    }

    private static LiveTrainStatusData dataWithRoute(List<RouteStop> route) {
        return new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 12, null,
                new CurrentLocation("KOTA", 5, "departed", true, false, true, 0.4, 92.5, 210),
                new NextHalt("RTM", "Ratlam Jn", 6, 550.0),
                route);
    }

    private static LiveTrainStatusData dataWithRoute(List<RouteStop> route, String lastUpdatedAt) {
        return new LiveTrainStatusData(
                "12952", "Rajdhani Express", "running", 12, lastUpdatedAt,
                new CurrentLocation("KOTA", 5, "departed", true, false, true, 0.4, 92.5, 210),
                new NextHalt("RTM", "Ratlam Jn", 6, 550.0),
                route);
    }

    @Test
    void extractsOnlyStationsTheTrainHasActuallyReached() {
        List<RouteStop> route = List.of(
                stop("NDLS", 1, "16:05"),   // reached - included
                stop("KOTA", 5, "20:51"),   // reached - included
                stop("BCT", 9, null));       // not reached yet - excluded

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(route));

        assertThat(observations).hasSize(2);
        assertThat(observations).extracting(HistoricalObservation::stationCode).containsExactly("NDLS", "KOTA");
    }

    @Test
    void mapsEveryFieldFromTheRouteStop() {
        List<RouteStop> route = List.of(stop("KOTA", 5, "20:51"));

        HistoricalObservation observation = mapper.toObservations("12952", dataWithRoute(route)).get(0);

        assertThat(observation.trainNumber()).isEqualTo("12952");
        assertThat(observation.journeyDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(observation.stationCode()).isEqualTo("KOTA");
        assertThat(observation.stationSequence()).isEqualTo(5);
        assertThat(observation.scheduledArrival()).isEqualTo("20:39");
        assertThat(observation.actualArrival()).isEqualTo("20:51");
        assertThat(observation.scheduledDeparture()).isEqualTo("20:41");
        assertThat(observation.actualDeparture()).isEqualTo("20:53");
        assertThat(observation.arrivalDelayMinutes()).isEqualTo(12);
        assertThat(observation.departureDelayMinutes()).isEqualTo(12);
        assertThat(observation.observedAt()).isEqualTo(Instant.parse("2026-09-09T15:00:00Z"));
        assertThat(observation.source()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void emptyOrNullRouteProducesNoObservations() {
        assertThat(mapper.toObservations("12952", dataWithRoute(List.of()))).isEmpty();
        assertThat(mapper.toObservations("12952", dataWithRoute(null))).isEmpty();
    }

    @Test
    void skipsAStopWithNoStationCodeEvenIfItHasAnActualArrival() {
        RouteStop malformed = new RouteStop(1, null, "Unknown", true, 0.0, 0.0,
                "20:39", "20:41", "20:51", "20:53", 12, 12, "departed", 465.0, null, "1");

        assertThat(mapper.toObservations("12952", dataWithRoute(List.of(malformed)))).isEmpty();
    }

    @Test
    void rejectsABlankStationCodeWithoutDiscardingOtherRowsInTheBatch() {
        RouteStop blank = new RouteStop(1, "   ", "Unknown", true, 0.0, 0.0,
                "20:39", "20:41", "20:51", "20:53", 12, 12, "departed", 465.0, null, "1");
        RouteStop valid = stop("KOTA", 5, "20:51");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(blank, valid)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).stationCode()).isEqualTo("KOTA");
        assertThat(metrics.observationsRejected()).isEqualTo(1);
    }

    @Test
    void rejectsAnImpossibleStationSequenceWithoutDiscardingOtherRowsInTheBatch() {
        RouteStop impossibleSequence = stop("NDLS", 0, "16:05");
        RouteStop valid = stop("KOTA", 5, "20:51");

        List<HistoricalObservation> observations =
                mapper.toObservations("12952", dataWithRoute(List.of(impossibleSequence, valid)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).stationCode()).isEqualTo("KOTA");
        assertThat(metrics.observationsRejected()).isEqualTo(1);
    }

    @Test
    void rejectsAnImplausibleDelayWithoutDiscardingOtherRowsInTheBatch() {
        RouteStop implausibleDelay = stop("NDLS", 1, "16:05", 999_999, 0);
        RouteStop valid = stop("KOTA", 5, "20:51");

        List<HistoricalObservation> observations =
                mapper.toObservations("12952", dataWithRoute(List.of(implausibleDelay, valid)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).stationCode()).isEqualTo("KOTA");
        assertThat(metrics.observationsRejected()).isEqualTo(1);
    }

    @Test
    void tracksReceivedAndRejectedCounts() {
        List<RouteStop> route = List.of(
                stop("NDLS", 1, "16:05"),
                stop("KOTA", 0, "20:51"),
                stop("BCT", 9, null));

        mapper.toObservations("12952", dataWithRoute(route));

        assertThat(metrics.observationsReceived()).isEqualTo(2);
        assertThat(metrics.observationsRejected()).isEqualTo(1);
    }

    // --- Phase 16E: origin / event-presence eligibility ---

    @Test
    void anOriginStopWithDepartureButNoArrivalIsEligible() {
        RouteStop origin = originStop("NDLS", 1, "08:05", 5);

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(origin)));

        assertThat(observations).hasSize(1);
        HistoricalObservation observation = observations.get(0);
        assertThat(observation.stationCode()).isEqualTo("NDLS");
        assertThat(observation.actualArrival()).isNull();
        assertThat(observation.scheduledArrival()).isNull();
        assertThat(observation.arrivalDelayMinutes()).isNull();
        assertThat(observation.actualDeparture()).isEqualTo("08:05");
        assertThat(observation.departureDelayMinutes()).isEqualTo(5);
    }

    @Test
    void anOriginStopWithMissingDepartureDelayIsStillEligible() {
        RouteStop origin = originStop("NDLS", 1, "08:05", null);

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(origin)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).departureDelayMinutes()).isNull();
    }

    @Test
    void anIntermediateStopWithArrivalButNoDepartureIsEligible() {
        RouteStop halted = new RouteStop(3, "KOTA", "Kota Jn", true, 0.0, 0.0,
                "20:39", null, "20:51", null, 12, null, "arrived", 465.0, null, "1");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(halted)));

        assertThat(observations).hasSize(1);
        HistoricalObservation observation = observations.get(0);
        assertThat(observation.actualArrival()).isEqualTo("20:51");
        assertThat(observation.actualDeparture()).isNull();
        assertThat(observation.departureDelayMinutes()).isNull();
    }

    @Test
    void anIntermediateStopWithDepartureButNoArrivalIsEligible() {
        RouteStop departureOnly = new RouteStop(3, "KOTA", "Kota Jn", true, 0.0, 0.0,
                null, "20:41", null, "20:53", null, 12, "departed", 465.0, null, "1");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(departureOnly)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).actualArrival()).isNull();
        assertThat(observations.get(0).actualDeparture()).isEqualTo("20:53");
    }

    @Test
    void aStopWithNeitherArrivalNorDepartureIsNotEligible() {
        RouteStop untouched = new RouteStop(9, "BCT", "Mumbai Central", true, 0.0, 0.0,
                "23:55", "23:59", null, null, null, null, "scheduled", 1200.0, null, "1");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(untouched)));

        assertThat(observations).isEmpty();
        assertThat(metrics.observationsReceived()).isZero();
    }

    // --- Phase 22D: "upcoming" stops carry a scheduled-time placeholder, not a real arrival ---

    @Test
    void anUpcomingStopWithAScheduledTimePlaceholderInActualArrivalIsNotEligible() {
        // Confirmed against a real RailRadar response for train 22415/NDLS: an unreached stop
        // reports status "upcoming" while actualArrival == scheduledArrival and delayArrival == 0
        // - a placeholder, not a real event. The pre-Phase-22D eligibility check (actualArrival
        // non-null) would have wrongly accepted this as a genuine arrival.
        RouteStop upcoming = new RouteStop(115, "NDLS", "New Delhi", true, 0.0, 0.0,
                "2026-09-11T14:05:00+05:30", null, "2026-09-11T14:05:00+05:30", null,
                0, null, "upcoming", 760.4, null, "12");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(upcoming)));

        assertThat(observations).isEmpty();
        assertThat(metrics.observationsReceived()).isEqualTo(1); // passed the null-check gate...
        assertThat(metrics.observationsRejected()).isEqualTo(1); // ...but correctly rejected here
    }

    @Test
    void anUpcomingStatusIsRejectedCaseInsensitively() {
        RouteStop upcoming = new RouteStop(115, "NDLS", "New Delhi", true, 0.0, 0.0,
                "14:05", null, "14:05", null, 0, null, "UPCOMING", 760.4, null, "12");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(upcoming)));

        assertThat(observations).isEmpty();
    }

    @Test
    void aGenuinelyDepartedStopWithTheSameShapeOfDataRemainsEligible() {
        // Regression guard: the fix must key off status alone, never off actualArrival happening
        // to equal scheduledArrival or delayArrival happening to be 0 - a real, genuinely-on-time
        // arrival looks identical in those two fields and must still be accepted.
        RouteStop onTimeButReal = new RouteStop(76, "BBL", "Balrai", false, 0.0, 0.0,
                "11:13", "11:13", "11:13", "11:13", 0, 0, "departed", 485.4, 95.8, "1");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(onTimeButReal)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).arrivalDelayMinutes()).isZero();
    }

    @Test
    void otherStatusesInABatchAreUnaffectedByOneUpcomingStop() {
        RouteStop departed = stop("BBL", 76, "11:13");
        RouteStop upcoming = new RouteStop(115, "NDLS", "New Delhi", true, 0.0, 0.0,
                "14:05", null, "14:05", null, 0, null, "upcoming", 760.4, null, "12");

        List<HistoricalObservation> observations =
                mapper.toObservations("12952", dataWithRoute(List.of(departed, upcoming)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).stationCode()).isEqualTo("BBL");
    }

    // --- Phase 23A: a genuine past event can never be after observedAt, regardless of status ---

    @Test
    void aDepartedStopWhoseActualArrivalIsStillInTheFutureRelativeToObservedAtIsNotEligible() {
        // Regression test for a real anomaly found via Phase 23 real-data accumulation: train
        // 12259 reported status "departed" (not "upcoming") for station LHU, yet actualArrival was
        // still ~15 minutes after the instant the response was fetched - RailRadar had evidently
        // already committed to a projected/estimated arrival before the train had genuinely reached
        // it. The status-based check alone does not catch this; only comparing the parsed
        // timestamp against observedAt does. FIXED_CLOCK (and therefore observedAt) is
        // 2026-09-09T15:00:00Z.
        RouteStop departedButActuallyFuture = new RouteStop(287, "LHU", "Loharu Jn", true, 0.0, 0.0,
                "2026-09-09T14:50:00Z", "2026-09-09T14:53:00Z",
                "2026-09-09T15:23:14Z", "2026-09-09T15:22:23Z",
                90, 89, "departed", 1615.3, 88.2, "3");

        List<HistoricalObservation> observations =
                mapper.toObservations("12259", dataWithRoute(List.of(departedButActuallyFuture)));

        assertThat(observations).isEmpty();
        assertThat(metrics.observationsReceived()).isEqualTo(1);
        assertThat(metrics.observationsRejected()).isEqualTo(1);
    }

    @Test
    void aDepartedStopWhoseActualDepartureIsStillInTheFutureRelativeToObservedAtIsNotEligible() {
        // Same invariant, the actualDeparture field independently - a stop could carry a
        // legitimate past actualArrival but a still-projected actualDeparture (e.g. a halt the
        // train has reached but not yet left).
        RouteStop departureStillFuture = new RouteStop(287, "LHU", "Loharu Jn", true, 0.0, 0.0,
                "2026-09-09T14:50:00Z", "2026-09-09T14:53:00Z",
                "2026-09-09T14:59:00Z", "2026-09-09T15:22:23Z",
                9, 89, "departed", 1615.3, 88.2, "3");

        List<HistoricalObservation> observations =
                mapper.toObservations("12259", dataWithRoute(List.of(departureStillFuture)));

        assertThat(observations).isEmpty();
    }

    @Test
    void aLegitimatePastActualArrivalRemainsEligible() {
        RouteStop genuinelyPast = new RouteStop(287, "LHU", "Loharu Jn", true, 0.0, 0.0,
                "2026-09-09T14:50:00Z", "2026-09-09T14:53:00Z",
                "2026-09-09T14:59:38Z", "2026-09-09T14:59:59Z",
                9, 6, "departed", 1615.3, 88.2, "3");

        List<HistoricalObservation> observations =
                mapper.toObservations("12259", dataWithRoute(List.of(genuinelyPast)));

        assertThat(observations).hasSize(1);
    }

    @Test
    void anActualArrivalExactlyEqualToObservedAtIsToleratedNotRejected() {
        // Boundary case: strictly-after is rejected, but "at the same instant" is a legitimate
        // near-instantaneous edge case, not treated as impossible.
        RouteStop exactlyNow = new RouteStop(287, "LHU", "Loharu Jn", true, 0.0, 0.0,
                "2026-09-09T14:50:00Z", "2026-09-09T14:53:00Z",
                "2026-09-09T15:00:00Z", null,
                10, null, "departed", 1615.3, 88.2, "3");

        List<HistoricalObservation> observations =
                mapper.toObservations("12259", dataWithRoute(List.of(exactlyNow)));

        assertThat(observations).hasSize(1);
    }

    @Test
    void anUnparseableActualArrivalFormatIsNotRejectedByTheFutureEventCheck() {
        // The opaque-string limitation is unchanged: a value that isn't a confirmed ISO-8601
        // offset date-time (e.g. the short "HH:mm" shape used elsewhere in these tests) is simply
        // not checked by this invariant, never rejected because it failed to parse.
        RouteStop shortTimeFormat = stop("BBL", 76, "11:13");

        List<HistoricalObservation> observations =
                mapper.toObservations("12952", dataWithRoute(List.of(shortTimeFormat)));

        assertThat(observations).hasSize(1);
    }

    // --- Phase 16E: station ordering ---

    @Test
    void aStopWithNullSequenceIsRetainedNotRejected() {
        RouteStop noSequence = stop("NDLS", null, "16:05");

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(noSequence)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).stationSequence()).isNull();
    }

    @Test
    void anOriginStopWithNullSequenceIsRetained() {
        RouteStop origin = originStop("NDLS", null, "08:05", 5);

        List<HistoricalObservation> observations = mapper.toObservations("12952", dataWithRoute(List.of(origin)));

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).stationSequence()).isNull();
    }

    // --- Phase 16E: journey identity ---

    @Test
    void journeyDateDefaultsToPlainCalendarDateWhenDayStartHourIsZero() {
        // FIXED_CLOCK = 2026-09-09T15:00:00Z, well after any midnight boundary either way.
        HistoricalObservation observation =
                mapper.toObservations("12952", dataWithRoute(List.of(stop("KOTA", 5, "20:51")))).get(0);

        assertThat(observation.journeyDate()).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void anObservationBeforeTheConfiguredOperatingDayStartHourIsAttributedToThePreviousCalendarDay() {
        // 2026-09-10T00:30:00Z - just after midnight UTC.
        Clock justAfterMidnight = Clock.fixed(Instant.parse("2026-09-10T00:30:00Z"), ZoneOffset.UTC);
        HistoricalObservationMapper overnightMapper = new HistoricalObservationMapper(
                justAfterMidnight,
                new HistoricalObservationValidationProperties(4320),
                new HistoricalJourneyDateProperties(3),
                new HistoricalDataMetrics());

        HistoricalObservation observation =
                overnightMapper.toObservations("12952", dataWithRoute(List.of(stop("KOTA", 5, "20:51")))).get(0);

        assertThat(observation.journeyDate()).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void anObservationAtOrAfterTheConfiguredOperatingDayStartHourUsesTheLiteralCalendarDate() {
        // 2026-09-10T03:30:00Z - after the configured 3 AM cutoff.
        Clock afterCutoff = Clock.fixed(Instant.parse("2026-09-10T03:30:00Z"), ZoneOffset.UTC);
        HistoricalObservationMapper overnightMapper = new HistoricalObservationMapper(
                afterCutoff,
                new HistoricalObservationValidationProperties(4320),
                new HistoricalJourneyDateProperties(3),
                new HistoricalDataMetrics());

        HistoricalObservation observation =
                overnightMapper.toObservations("12952", dataWithRoute(List.of(stop("KOTA", 5, "20:51")))).get(0);

        assertThat(observation.journeyDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void journeyDateIsUnaffectedByOpaqueResponseTimestampFields() {
        // "Unavailable journey-date information": there is no trustworthy date field in the
        // response at all, so the mapper must never derive journeyDate from lastUpdatedAt or any
        // other opaque string - only from its own injected Clock.
        List<RouteStop> route = List.of(stop("KOTA", 5, "20:51"));

        HistoricalObservation withNullTimestamp =
                mapper.toObservations("12952", dataWithRoute(route, null)).get(0);
        HistoricalObservation withBogusTimestamp =
                mapper.toObservations("12952", dataWithRoute(route, "not-a-real-timestamp")).get(0);

        assertThat(withNullTimestamp.journeyDate()).isEqualTo(withBogusTimestamp.journeyDate());
    }
}
