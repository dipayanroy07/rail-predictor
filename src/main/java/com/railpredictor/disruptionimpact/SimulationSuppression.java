package com.railpredictor.disruptionimpact;

import com.railpredictor.model.domain.DisruptionImpact;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.enums.DisruptionType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Decides which <em>simulated</em> {@code DisruptionModel}s must be suppressed for a run because a
 * <em>real</em>, currently-active disruption of the matching kind already exists (Phase 19) - the
 * mechanism that keeps real and simulated disruption data mutually exclusive per type, never
 * additive for the same underlying real-world cause (e.g. never a real signal failure's estimated
 * impact <em>plus</em> {@code SignalHaltModel}'s own probabilistic "what if" for the same section).
 *
 * <p>A real disruption suppresses its simulated counterpart <b>regardless of whether this policy
 * could estimate a delay figure for it</b> - {@link DisruptionImpactAssessment#contributingImpacts()}
 * includes both {@code ESTIMATED} and {@code PRESENT_BUT_NOT_ESTIMABLE} entries, and both must
 * suppress the same way: a confirmed real signal failure means the simulated "what if a signal
 * fails" hypothesis is no longer meaningful, whether or not this phase's policy could quantify the
 * real one's minutes yet.
 *
 * <p>{@link RailwayDisruptionType#HEAVY_RAIN}... - there is no such value; weather-driven
 * disruptions ({@code HEAVY_RAIN}/{@code DENSE_FOG}) have no {@code RailwayDisruptionType}
 * counterpart in this phase's scope (weather already has its own real/mock provider - see
 * {@code WeatherProvider}) and are never suppressed here. {@code ROUTE_DIVERSION}/
 * {@code MAINTENANCE_BLOCK}/{@code OTHER} have no simulated counterpart either and are ignored.
 */
public final class SimulationSuppression {

    private SimulationSuppression() {
    }

    public static Set<DisruptionType> suppressedSimulationTypes(DisruptionImpactAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");

        Set<DisruptionType> suppressed = EnumSet.noneOf(DisruptionType.class);
        for (DisruptionImpact impact : assessment.contributingImpacts()) {
            DisruptionType simulatedCounterpart = switch (impact.disruptionType()) {
                case TEMPORARY_SPEED_RESTRICTION -> DisruptionType.SPEED_RESTRICTION;
                case ENGINEERING_BLOCK -> DisruptionType.ENGINEERING_BLOCK;
                case SIGNAL_FAILURE -> DisruptionType.SIGNAL_HALT;
                case CONGESTION -> DisruptionType.HIGH_CONGESTION;
                case ROUTE_DIVERSION, MAINTENANCE_BLOCK, OTHER -> null;
            };
            if (simulatedCounterpart != null) {
                suppressed.add(simulatedCounterpart);
            }
        }
        return suppressed;
    }
}
