package com.railpredictor.model.domain;

import java.time.Instant;

/**
 * One *observed, real* railway operational disruption (Phase 18) - a reported operational fact
 * (a temporary speed restriction, engineering block, signal failure, etc.), never itself a delay
 * estimate. Deliberately separate from {@link com.railpredictor.model.domain.DisruptionResult}
 * (the *simulated* output of a {@code DisruptionModel}): this record represents "what disruption
 * is reported to exist", not "how many minutes it should cost" - converting one into the other is
 * an explicitly deferred, separately-designed future policy (see docs/prediction-model.md's Phase
 * 18 notes). No code in this application currently reads this record's fields to influence a
 * prediction.
 *
 * <p><b>Only fields a real source could plausibly report are included</b> - deliberately no
 * {@code delayMinutes} (an observation is not automatically a delay estimate) and no invented
 * severity taxonomy (see {@code severity}, kept as a free-form nullable string rather than an
 * enum this application has no evidence to define).
 *
 * <ul>
 *   <li>{@code trainNumber} - {@code null} for a route-wide disruption not tied to one specific
 *       train (e.g. a section-wide TSR); non-null for a train-specific operational notice.</li>
 *   <li>{@code fromStationCode}/{@code toStationCode} - the section this disruption applies to,
 *       using the same plain station-code identity {@code RouteSection}/
 *       {@code HistoricalSectionDelayProvider} already use - no new section-identity concept is
 *       invented.</li>
 *   <li>{@code restrictedSpeedKmh} - only populated when a source explicitly supplies a speed
 *       limit (relevant to {@link RailwayDisruptionType#TEMPORARY_SPEED_RESTRICTION}); {@code null}
 *       otherwise, never inferred.</li>
 *   <li>{@code severity} - a free-form string exactly as the source describes it, or {@code null};
 *       never a fabricated severity level.</li>
 *   <li>{@code effectiveFrom}/{@code effectiveUntil} - the disruption's own reported validity
 *       window, both nullable independently. See {@code RailwayDisruptionStatusClassifier} for how
 *       these are interpreted relative to a reference instant - never assume an undated disruption
 *       is currently active.</li>
 *   <li>{@code observedAt} - when this application learned of the disruption (mirrors
 *       {@code HistoricalObservation.observedAt()}'s "when we recorded this fact" semantics, not
 *       the disruption's own start time).</li>
 *   <li>{@code source} - provenance string (e.g. {@code DataProvenance.MOCK}); mock-derived
 *       disruptions must never be mistaken for a real operational report.</li>
 *   <li>{@code sourceReference} - the source's own identifier/reference for this disruption, if
 *       it supplies one (e.g. a bulletin/notice number); {@code null} otherwise.</li>
 * </ul>
 */
public record RailwayDisruption(
        RailwayDisruptionType type,
        String trainNumber,
        String fromStationCode,
        String toStationCode,
        Double restrictedSpeedKmh,
        String severity,
        Instant effectiveFrom,
        Instant effectiveUntil,
        Instant observedAt,
        String source,
        String sourceReference) {

    public RailwayDisruption {
        type = Guard.requireNonNull(type, "type");
        fromStationCode = Guard.requireNonBlank(fromStationCode, "fromStationCode");
        toStationCode = Guard.requireNonBlank(toStationCode, "toStationCode");
        if (restrictedSpeedKmh != null) {
            Guard.requireNonNegative(restrictedSpeedKmh, "restrictedSpeedKmh");
        }
        observedAt = Guard.requireNonNull(observedAt, "observedAt");
        source = Guard.requireNonBlank(source, "source");
        if (effectiveFrom != null && effectiveUntil != null && effectiveFrom.isAfter(effectiveUntil)) {
            throw new IllegalArgumentException(
                    "effectiveFrom (" + effectiveFrom + ") must not be after effectiveUntil (" + effectiveUntil + ")");
        }
    }
}
