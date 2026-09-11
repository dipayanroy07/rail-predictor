package com.railpredictor.model.domain;

/**
 * The kinds of *observed, real* railway operational disruption this application's domain model can
 * represent (Phase 18) - deliberately separate from {@link com.railpredictor.model.enums.DisruptionType}
 * (the *simulated* disruption models {@code HeavyRainModel}/{@code CongestionModel}/etc. produce).
 * An observed disruption is a reported operational fact; a simulated disruption is a modelled
 * hypothesis about delay - see {@code RailwayDisruption}'s own Javadoc for the full distinction.
 *
 * <p>This vocabulary reflects real categories investigated during this phase's source research
 * (docs/architecture.md's Phase 18 notes) - it does not imply any of them currently has a live,
 * machine-readable feed backing it (see {@code UnavailableRailwayDisruptionProvider}).
 */
public enum RailwayDisruptionType {

    /** A temporary speed restriction (TSR) - a maximum permitted speed over a section, for a
     * reported reason (track work, subsidence, etc.). */
    TEMPORARY_SPEED_RESTRICTION,

    /** Planned track/infrastructure maintenance work (a "block") that restricts or halts movement
     * over a section for a scheduled window. */
    ENGINEERING_BLOCK,

    /** A signalling equipment failure causing trains to be held or slowed. */
    SIGNAL_FAILURE,

    /** Reported operational congestion (section/yard occupancy) distinct from a specific
     * infrastructure cause. */
    CONGESTION,

    /** The train's route has been diverted from its normal path. */
    ROUTE_DIVERSION,

    /** A maintenance block not otherwise categorized above. */
    MAINTENANCE_BLOCK,

    /** A real, reported disruption whose category the source did not classify into any of the
     * above - never used to fabricate a more specific category than the source actually gave. */
    OTHER
}
