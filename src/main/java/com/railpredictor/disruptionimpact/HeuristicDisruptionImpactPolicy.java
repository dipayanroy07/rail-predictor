package com.railpredictor.disruptionimpact;

import com.railpredictor.config.DisruptionImpactProperties;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DisruptionImpact;
import com.railpredictor.model.domain.DisruptionImpactStatus;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionType;
import com.railpredictor.model.domain.RouteSection;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The only {@link DisruptionImpactPolicy} implementation this codebase has (Phase 19). Its name is
 * deliberate: every number it produces beyond {@link RailwayDisruptionType#TEMPORARY_SPEED_RESTRICTION}'s
 * physics-based estimate is a <b>provisional engineering heuristic</b>, never an empirically
 * validated coefficient - {@link #calibrationStatus()} always reports
 * {@link CalibrationStatus#INSUFFICIENT_DATA}. See docs/prediction-model.md's Phase 19 notes for
 * the full evidence-vs-heuristic accounting this class's own Javadoc summarizes below.
 *
 * <p>Each {@link RailwayDisruptionType} is evaluated independently - there is no single shared
 * formula:
 *
 * <ul>
 *   <li><b>{@link RailwayDisruptionType#TEMPORARY_SPEED_RESTRICTION}</b> - the only type with a
 *       physically-grounded (not fabricated) computation: given the train's current speed, the
 *       disruption's own reported {@code restrictedSpeedKmh}, and the section's own
 *       {@code distanceKm}, the extra time to cover that distance at the restricted speed instead
 *       of the current speed is basic kinematics (time = distance / speed), mirroring
 *       {@code SpeedRestrictionModel}'s own established formula for the same physical
 *       relationship. This is still a simplification (it assumes the train runs at exactly the
 *       restricted speed for the section's entire length, ignoring deceleration/acceleration
 *       profiles this codebase has no justified data for - see item 7 of this phase's own
 *       instructions) - a first-order physical estimate, not an empirically-validated model of
 *       real speed-restriction delay outcomes. Any of the three required inputs missing, zero, or
 *       non-restrictive (restricted speed not actually slower than current speed) yields
 *       {@link DisruptionImpactStatus#PRESENT_BUT_NOT_ESTIMABLE} - never a fabricated number.</li>
 *   <li><b>{@link RailwayDisruptionType#ENGINEERING_BLOCK}</b>,
 *       <b>{@link RailwayDisruptionType#SIGNAL_FAILURE}</b>,
 *       <b>{@link RailwayDisruptionType#CONGESTION}</b> - no real source in this codebase supplies
 *       a duration or severity figure precise enough to compute from, so each uses its own
 *       configurable flat default ({@code railway-disruption-impact.*-default-delay-minutes}) -
 *       explicitly a provisional heuristic, not derived from evidence. {@code severity} (a
 *       free-form string on {@code RailwayDisruption}) is captured for audit but deliberately
 *       <b>not</b> used to scale these defaults - this codebase has no reliable, source-guaranteed
 *       severity vocabulary to interpret, and inventing one would be exactly the kind of
 *       unjustified inference this phase's own instructions warn against.</li>
 *   <li><b>{@link RailwayDisruptionType#ROUTE_DIVERSION}</b>,
 *       <b>{@link RailwayDisruptionType#MAINTENANCE_BLOCK}</b>,
 *       <b>{@link RailwayDisruptionType#OTHER}</b> - this policy has no impact rule for these at
 *       all yet; always {@link DisruptionImpactStatus#PRESENT_BUT_NOT_ESTIMABLE}.</li>
 * </ul>
 *
 * <p>Every estimated figure (including the speed-restriction physics result) is capped at
 * {@code railway-disruption-impact.max-single-disruption-delay-minutes} - a safety bound against a
 * pathological input, not a claim about real-world plausibility.
 */
@Component
public class HeuristicDisruptionImpactPolicy implements DisruptionImpactPolicy {

    private final DisruptionImpactProperties properties;

    public HeuristicDisruptionImpactPolicy(DisruptionImpactProperties properties) {
        this.properties = properties;
    }

    @Override
    public DisruptionImpact evaluate(RailwayDisruption disruption, RouteSection section, LiveTrainData train) {
        Objects.requireNonNull(disruption, "disruption");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(train, "train");

        return switch (disruption.type()) {
            case TEMPORARY_SPEED_RESTRICTION -> evaluateSpeedRestriction(disruption, section, train);
            case ENGINEERING_BLOCK -> flatHeuristic(disruption, properties.engineeringBlockDefaultDelayMinutes(),
                    "Engineering block reported - no real duration/severity figure available; "
                            + "using the configured provisional default of "
                            + properties.engineeringBlockDefaultDelayMinutes() + " min (heuristic, not empirically calibrated)");
            case SIGNAL_FAILURE -> flatHeuristic(disruption, properties.signalFailureDefaultDelayMinutes(),
                    "Signal failure reported - no real duration figure available; using the configured "
                            + "provisional default of " + properties.signalFailureDefaultDelayMinutes()
                            + " min (heuristic, not empirically calibrated)");
            case CONGESTION -> flatHeuristic(disruption, properties.congestionDefaultDelayMinutes(),
                    "Congestion reported - severity is not used to scale this estimate (no reliable "
                            + "vocabulary to interpret it); using the configured provisional default of "
                            + properties.congestionDefaultDelayMinutes() + " min (heuristic, not empirically calibrated)");
            case ROUTE_DIVERSION, MAINTENANCE_BLOCK, OTHER -> notEstimable(disruption,
                    "No impact rule exists yet for " + disruption.type() + " - present but not estimable");
        };
    }

    @Override
    public CalibrationStatus calibrationStatus() {
        return CalibrationStatus.INSUFFICIENT_DATA;
    }

    /**
     * Basic kinematics (time = distance / speed) using the disruption's own reported restricted
     * speed, the train's own current speed, and the section's own distance - never fabricated
     * inputs. See this class's own Javadoc for why this is a physically-grounded first-order
     * estimate, not an empirically-validated one, and is still capped like every other estimate.
     */
    private DisruptionImpact evaluateSpeedRestriction(RailwayDisruption disruption, RouteSection section, LiveTrainData train) {
        Double restrictedSpeedKmh = disruption.restrictedSpeedKmh();
        Double normalSpeedKmh = train.speedKmh();
        Double distanceKm = section.distanceKm();

        if (restrictedSpeedKmh == null || restrictedSpeedKmh <= 0
                || normalSpeedKmh == null || normalSpeedKmh <= 0
                || distanceKm == null || distanceKm <= 0
                || restrictedSpeedKmh >= normalSpeedKmh) {
            return notEstimable(disruption,
                    "Temporary speed restriction reported, but a defensible delay could not be computed - "
                            + "missing or non-restrictive restrictedSpeedKmh/current speed/section distance");
        }

        double normalTimeMinutes = distanceKm / normalSpeedKmh * 60.0;
        double restrictedTimeMinutes = distanceKm / restrictedSpeedKmh * 60.0;
        int delayMinutes = capped((int) Math.round(restrictedTimeMinutes - normalTimeMinutes));

        return new DisruptionImpact(
                disruption.type(),
                DisruptionImpactStatus.ESTIMATED,
                delayMinutes,
                "Speed restriction to " + restrictedSpeedKmh + " km/h over " + distanceKm
                        + " km versus current speed " + normalSpeedKmh + " km/h computes to " + delayMinutes
                        + " min (physically-grounded first-order estimate, not empirically validated - "
                        + "ignores real deceleration/acceleration profiles)",
                disruption.source());
    }

    private DisruptionImpact flatHeuristic(RailwayDisruption disruption, int defaultMinutes, String rationale) {
        return new DisruptionImpact(
                disruption.type(), DisruptionImpactStatus.ESTIMATED, capped(defaultMinutes), rationale, disruption.source());
    }

    private static DisruptionImpact notEstimable(RailwayDisruption disruption, String rationale) {
        return new DisruptionImpact(
                disruption.type(), DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null, rationale, disruption.source());
    }

    private int capped(int minutes) {
        return Math.max(0, Math.min(minutes, properties.maxSingleDisruptionDelayMinutes()));
    }
}
