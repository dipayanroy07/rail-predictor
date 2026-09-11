package com.railpredictor.railradar.mapper;

import com.railpredictor.config.HistoricalJourneyDateProperties;
import com.railpredictor.config.HistoricalObservationValidationProperties;
import com.railpredictor.historical.HistoricalDataMetrics;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.railradar.dto.LiveTrainStatusData;
import com.railpredictor.railradar.dto.RouteStop;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts raw {@link HistoricalObservation}s from RailRadar's own {@code route[]} data - the
 * same {@link LiveTrainStatusData} already fetched for {@link LiveTrainDataMapper}, never a
 * second RailRadar call. RailRadar DTOs must never be visible outside this package's mapping
 * step (see {@link LiveTrainDataMapper}'s own Javadoc) - this class is the one place
 * {@link RouteStop} is translated into historical-observation data.
 *
 * <p><b>Eligibility (Phase 16E, corrected by Phase 22D):</b> a stop produces an observation when
 * it has <em>either</em> a genuine arrival or departure event
 * ({@code actualArrival() != null || actualDeparture() != null}) <b>and</b> {@code status()} does
 * not indicate the stop hasn't happened yet - see {@link #rejectionReason} for the exact check and
 * why the null check on its own is not sufficient. Before Phase 16E, eligibility required an
 * arrival, which silently and systematically excluded every journey's <b>origin station</b> (a
 * train never "arrives" at its own origin - it only departs). That exclusion is fixed: an origin
 * stop with a departure but no arrival is eligible. Nothing is fabricated to make this work -
 * {@code actualArrival}/{@code arrivalDelayMinutes}/{@code scheduledArrival} stay exactly whatever
 * RailRadar supplied, and an intermediate stop may just as validly carry only one of the two
 * events, or both, or (before it's reached, and correctly rejected) neither - see
 * {@link HistoricalObservation}'s own Javadoc for why every event field is independently nullable.
 *
 * <p><b>Phase 22D finding:</b> RailRadar does <em>not</em> leave {@code actualArrival}/
 * {@code actualDeparture} null for a stop it hasn't reached - it populates them with the scheduled
 * time (and the delay fields with {@code 0}), distinguishable only via a separate {@code status}
 * field ({@code "upcoming"} confirmed against a real response, contrasted with {@code "departed"}
 * for a genuinely-reached stop). Before this phase, this mapper only checked the actual*
 * fields for non-null, which - for an "upcoming" stop - incorrectly produced a real-looking
 * {@link HistoricalObservation} claiming a real arrival that had not happened. See
 * docs/historical-data-design.md's Phase 22D section for the full incident, evidence, and residual
 * risk (only the one confirmed "not yet happened" status value is excluded; an unobserved RailRadar
 * status string with the same meaning would not yet be caught).
 *
 * <p>Each eligible stop is validated independently (Phase 16C data-quality gate, unchanged by
 * Phase 16E - it never depended on arrival/departure presence) before its
 * {@link HistoricalObservation} is constructed: a blank station code or an impossible station
 * sequence is rejected outright, and an implausible delay value (beyond
 * {@link HistoricalObservationValidationProperties#maxPlausibleDelayMinutes()}) is treated as
 * corrupt rather than a genuinely extreme delay. One row failing validation - or even throwing
 * unexpectedly during construction - must never discard the rest of the batch, so rows are
 * processed in an explicit loop rather than a single {@code stream().map()} chain. Timestamp-
 * ordering checks (e.g. departure before arrival) are NOT performed: RailRadar's raw
 * scheduled/actual time strings are deliberately kept as opaque strings (their format was never
 * confirmed against a real API response), so no structured ordering comparison is possible yet -
 * this is a documented limitation, not an oversight. Train-number format validation is also not
 * duplicated here - that is the REST controller's responsibility.
 *
 * <p><b>Station ordering (Phase 16E):</b> {@code RouteStop.sequence()} is the only ordinal
 * anywhere in RailRadar's live-status response ({@code LiveTrainStatusData}, {@code
 * CurrentLocation}, {@code NextHalt}, and {@code ResponseMeta} carry no independent per-stop
 * position field - {@code CurrentLocation.sequence()}/{@code NextHalt.sequence()} only describe
 * the current/next stop specifically, not a usable ordinal for every stop in {@code route[]}).
 * It is nullable, and this mapper never invents a substitute: a stop with a null sequence is still
 * persisted (when otherwise valid) exactly as null. A future section-reconstruction phase MUST
 * exclude null-sequence rows from adjacency pairing rather than guessing their position - this
 * mapper only guarantees the value is never fabricated, it cannot itself enforce how a future
 * phase uses it.
 *
 * <p><b>Journey identity (Phase 16E):</b> {@code journeyDate} is computed via
 * {@link HistoricalJourneyDateProperties#operatingDayStartHour()} against "now" (via the injected
 * {@link Clock}) - see that class's Javadoc for the full reasoning on why this is an
 * <em>observation-date</em>, not a verified physical-journey identifier, and why no RailRadar
 * field can supply anything better today.
 */
@Component
public class HistoricalObservationMapper {

    private static final Logger log = LoggerFactory.getLogger(HistoricalObservationMapper.class);

    private final Clock clock;
    private final HistoricalObservationValidationProperties validationProperties;
    private final HistoricalJourneyDateProperties journeyDateProperties;
    private final HistoricalDataMetrics metrics;

    public HistoricalObservationMapper(
            Clock clock,
            HistoricalObservationValidationProperties validationProperties,
            HistoricalJourneyDateProperties journeyDateProperties,
            HistoricalDataMetrics metrics) {
        this.clock = clock;
        this.validationProperties = validationProperties;
        this.journeyDateProperties = journeyDateProperties;
        this.metrics = metrics;
    }

    public List<HistoricalObservation> toObservations(String trainNumber, LiveTrainStatusData data) {
        if (data.route() == null) {
            return List.of();
        }

        LocalDate journeyDate = operatingDate(clock, journeyDateProperties.operatingDayStartHour());
        Instant observedAt = Instant.now(clock);

        List<HistoricalObservation> observations = new ArrayList<>();
        for (RouteStop stop : data.route()) {
            if (stop.actualArrival() == null && stop.actualDeparture() == null) {
                continue;
            }
            metrics.observationReceived();
            String rejectionReason = rejectionReason(stop, observedAt);
            if (rejectionReason != null) {
                metrics.observationRejected();
                log.debug(
                        "Rejected historical observation for train {} station {}: {}",
                        trainNumber, stop.stationCode(), rejectionReason);
                continue;
            }
            try {
                observations.add(toObservation(trainNumber, journeyDate, observedAt, stop));
            } catch (RuntimeException e) {
                metrics.observationRejected();
                log.debug(
                        "Rejected historical observation for train {} station {}: {}",
                        trainNumber, stop.stationCode(), e.getMessage());
            }
        }
        return List.copyOf(observations);
    }

    /**
     * "Today", per the given clock, unless the current hour is before {@code dayStartHour}, in
     * which case "yesterday" - the same operating-day convention transit systems commonly use so
     * that a journey which begins before midnight isn't attributed a different calendar date
     * merely because it's still running (or being polled) just after it. {@code dayStartHour = 0}
     * (the default) makes this identical to plain {@code LocalDate.now(clock)} - no behaviour
     * change unless explicitly configured.
     */
    private static LocalDate operatingDate(Clock clock, int dayStartHour) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return now.getHour() < dayStartHour ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    /**
     * Phase 22D finding, confirmed against a real RailRadar response: for a stop it has not yet
     * reached, RailRadar does not leave {@code actualArrival}/{@code actualDeparture} null - it
     * populates them with the scheduled time (and {@code delayArrival}/{@code delayDeparture}
     * with {@code 0}), while marking {@code status: "upcoming"}. A genuinely-reached stop observed
     * the same way carried {@code status: "departed"}. This means the null-check above is
     * necessary but not sufficient: {@code status} is the only field that actually distinguishes
     * a real event from a scheduled placeholder, so a stop reporting {@code "upcoming"} must never
     * become a {@link com.railpredictor.model.domain.HistoricalObservation}, no matter what its
     * {@code actual}/{@code delay} fields say.
     *
     * <p>Only {@code "upcoming"} is excluded here because it is the only "not yet happened" status
     * value this codebase has confirmed against a real response - see
     * docs/historical-data-design.md's Phase 22D notes for the full finding and its residual risk
     * (an as-yet-unobserved RailRadar status string with the same "not yet happened" meaning would
     * not be caught by this check).
     *
     * <p><b>Phase 23A finding:</b> the {@code status} check above is necessary but still not
     * sufficient - a real response for train 12259 showed a stop marked {@code status: "departed"}
     * (not {@code "upcoming"}) whose {@code actualArrival} was nonetheless still ~15 minutes in the
     * future relative to when the response was fetched (RailRadar had apparently already committed
     * to a projected/estimated arrival for a fast-approaching stop before the train had genuinely
     * reached it). No additional status string was involved - a second, orthogonal, always-on
     * invariant is needed: {@code actualArrival}/{@code actualDeparture}, whenever they parse as a
     * real ISO-8601 offset date-time (the only format ever confirmed against a real response - see
     * class Javadoc), can never legitimately be after {@code observedAt} (the instant this
     * application fetched/recorded the response) - a genuine past event cannot occur after the
     * moment it was observed, regardless of what status string accompanies it. A value that fails
     * to parse as that format is left unchecked here (opaque-string limitation, unchanged) rather
     * than rejected, so this can never regress a caller supplying some other, still-unconfirmed
     * timestamp shape.
     */
    private String rejectionReason(RouteStop stop, Instant observedAt) {
        if (stop.stationCode() == null || stop.stationCode().isBlank()) {
            return "missing station code";
        }
        if ("upcoming".equalsIgnoreCase(stop.status())) {
            return "stop has not yet occurred (status=upcoming) - RailRadar reports a scheduled-time "
                    + "placeholder in actualArrival/actualDeparture for upcoming stops, not a real event";
        }
        if (stop.sequence() != null && stop.sequence() <= 0) {
            return "impossible station sequence: " + stop.sequence();
        }
        if (isImplausible(stop.delayArrival()) || isImplausible(stop.delayDeparture())) {
            return "implausible delay value beyond " + validationProperties.maxPlausibleDelayMinutes() + " minutes";
        }
        String futureActualArrivalReason = futureActualEventReason("actualArrival", stop.actualArrival(), observedAt);
        if (futureActualArrivalReason != null) {
            return futureActualArrivalReason;
        }
        String futureActualDepartureReason =
                futureActualEventReason("actualDeparture", stop.actualDeparture(), observedAt);
        if (futureActualDepartureReason != null) {
            return futureActualDepartureReason;
        }
        return null;
    }

    /**
     * @return a rejection reason when {@code rawTimestamp} parses as a real ISO-8601 offset
     *         date-time strictly after {@code observedAt}; {@code null} when it is null, does not
     *         parse in that format (opaque-string limitation - never rejected on that basis alone),
     *         or is at/before {@code observedAt} (exactly equal is deliberately tolerated as a
     *         legitimate near-instantaneous boundary case, not treated as impossible).
     */
    private static String futureActualEventReason(String fieldName, String rawTimestamp, Instant observedAt) {
        if (rawTimestamp == null) {
            return null;
        }
        Instant parsed;
        try {
            parsed = OffsetDateTime.parse(rawTimestamp).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
        if (parsed.isAfter(observedAt)) {
            return fieldName + " (" + rawTimestamp + ") is after observedAt (" + observedAt + ") - a genuine "
                    + "past event cannot occur after the moment this application observed it";
        }
        return null;
    }

    private boolean isImplausible(Integer delayMinutes) {
        return delayMinutes != null && Math.abs(delayMinutes) > validationProperties.maxPlausibleDelayMinutes();
    }

    private static HistoricalObservation toObservation(
            String trainNumber, LocalDate journeyDate, Instant observedAt, RouteStop stop) {
        return new HistoricalObservation(
                trainNumber,
                journeyDate,
                stop.stationCode(),
                stop.sequence(),
                stop.scheduledArrival(),
                stop.actualArrival(),
                stop.scheduledDeparture(),
                stop.actualDeparture(),
                stop.delayArrival(),
                stop.delayDeparture(),
                observedAt,
                DataProvenance.RAILRADAR);
    }
}
