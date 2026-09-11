package com.railpredictor.disruptionimpact;

import com.railpredictor.config.DisruptionImpactProperties;
import com.railpredictor.model.domain.CalibrationStatus;
import com.railpredictor.model.domain.DisruptionImpact;
import com.railpredictor.model.domain.DisruptionImpactAssessment;
import com.railpredictor.model.domain.DisruptionImpactStatus;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionAvailability;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RailwayDisruptionStatus;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.railwaydisruption.RailwayDisruptionStatusClassifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Orchestrates {@code RailwayDisruptionStatusClassifier} (temporal filtering) and
 * {@link DisruptionImpactPolicy} (per-disruption translation) into one
 * {@link DisruptionImpactAssessment} for a section (Phase 19) - the
 * "{@code RailwayDisruptionProvider} → {@code DisruptionImpactPolicy} →
 * {@code DisruptionImpactAggregator} → {@code PredictionEngine}" pipeline's penultimate stage.
 * Pure aside from the injected policy/properties - no persistence, no provider access; mirrors
 * this codebase's established split between pure aggregation and provider orchestration (e.g.
 * {@code RemainingRouteHistoricalAggregator}).
 *
 * <p><b>Temporal filtering</b>: only disruptions {@code RailwayDisruptionStatusClassifier} reports
 * as {@code ACTIVE} as of {@code referenceInstant} are ever evaluated - future/expired/undated
 * disruptions are treated exactly like "not currently known" ({@link DisruptionImpactStatus#NO_KNOWN_DISRUPTION}),
 * never applied.
 *
 * <p><b>De-duplication</b>: disruptions sharing the same (type, trainNumber, fromStationCode,
 * toStationCode) identity are collapsed to the single most-recently-observed one before
 * evaluation - a re-reported disruption must never be counted twice.
 *
 * <p><b>Aggregation</b>: distinct disruption types/sections are summed (each is an independent,
 * plausible additive cause), but the total is capped at
 * {@code railway-disruption-impact.max-aggregate-delay-minutes} - see
 * {@link DisruptionImpactAssessment#cappedByMaximumAggregate()}.
 */
@Component
public class DisruptionImpactAggregator {

    private final DisruptionImpactPolicy policy;
    private final DisruptionImpactProperties properties;

    public DisruptionImpactAggregator(DisruptionImpactPolicy policy, DisruptionImpactProperties properties) {
        this.policy = policy;
        this.properties = properties;
    }

    public DisruptionImpactAssessment assess(
            RailwayDisruptionQueryResult queryResult, RouteSection section, LiveTrainData train, Instant referenceInstant) {
        Objects.requireNonNull(queryResult, "queryResult");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(train, "train");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        CalibrationStatus calibrationStatus = policy.calibrationStatus();

        if (queryResult.availability() == RailwayDisruptionAvailability.UNAVAILABLE) {
            return new DisruptionImpactAssessment(
                    DisruptionImpactStatus.DATA_UNAVAILABLE, null, List.of(), false, calibrationStatus);
        }

        List<RailwayDisruption> active = queryResult.disruptions().stream()
                .filter(d -> RailwayDisruptionStatusClassifier.classify(d, referenceInstant) == RailwayDisruptionStatus.ACTIVE)
                .toList();

        List<RailwayDisruption> deduplicated = deduplicate(active);

        if (deduplicated.isEmpty()) {
            return new DisruptionImpactAssessment(
                    DisruptionImpactStatus.NO_KNOWN_DISRUPTION, 0, List.of(), false, calibrationStatus);
        }

        List<DisruptionImpact> impacts = deduplicated.stream()
                .map(d -> policy.evaluate(d, section, train))
                .toList();

        int rawSum = impacts.stream()
                .filter(i -> i.status() == DisruptionImpactStatus.ESTIMATED)
                .mapToInt(DisruptionImpact::additionalDelayMinutes)
                .sum();
        boolean anyEstimated = impacts.stream().anyMatch(i -> i.status() == DisruptionImpactStatus.ESTIMATED);

        if (!anyEstimated) {
            return new DisruptionImpactAssessment(
                    DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE, null, impacts, false, calibrationStatus);
        }

        boolean capped = rawSum > properties.maxAggregateDelayMinutes();
        int total = Math.min(rawSum, properties.maxAggregateDelayMinutes());

        return new DisruptionImpactAssessment(DisruptionImpactStatus.ESTIMATED, total, impacts, capped, calibrationStatus);
    }

    /** Keeps only the most-recently-observed disruption per (type, trainNumber, fromStationCode,
     * toStationCode) identity - a re-reported disruption must never be double-counted. */
    private static List<RailwayDisruption> deduplicate(List<RailwayDisruption> disruptions) {
        Map<String, RailwayDisruption> latestByIdentity = new LinkedHashMap<>();
        for (RailwayDisruption disruption : disruptions) {
            String key = identityKey(disruption);
            RailwayDisruption existing = latestByIdentity.get(key);
            if (existing == null || disruption.observedAt().isAfter(existing.observedAt())) {
                latestByIdentity.put(key, disruption);
            }
        }
        List<RailwayDisruption> result = new ArrayList<>(latestByIdentity.values());
        result.sort(Comparator.comparing(RailwayDisruption::observedAt));
        return result;
    }

    private static String identityKey(RailwayDisruption disruption) {
        return disruption.type() + "|" + disruption.trainNumber() + "|" + disruption.fromStationCode()
                + "|" + disruption.toStationCode();
    }
}
