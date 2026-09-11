package com.railpredictor.railwaydisruption;

import com.railpredictor.model.domain.RailwayDisruptionAvailability;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RouteSection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The honest default: no reliable, publicly accessible, machine-readable source of real Indian
 * Railways operational disruption data (temporary speed restrictions, engineering blocks, signal
 * failures, congestion) was found during this phase's research to exist in a form this application
 * can integrate (see docs/architecture.md's Phase 18 notes for the full source-by-source
 * findings). Rather than fabricate "real" data or silently substitute mock data, this provider
 * always reports {@link RailwayDisruptionAvailability#UNAVAILABLE} - an explicit, honest signal
 * that must never be interpreted as "no disruptions exist" (see {@code RailwayDisruptionAvailability}'s
 * own Javadoc).
 *
 * <p>Active whenever {@code railway-disruption.provider} is {@code unavailable} or unset (the
 * default) - see {@link MockRailwayDisruptionProvider} for the deterministic test/demo alternative
 * and docs/configuration.md for the provider-selection rules. Exactly one
 * {@link RailwayDisruptionProvider} bean ever exists; mirrors
 * {@code MockHistoricalDelayProvider}/{@code MockWeatherProvider}'s exact deterministic-selection
 * pattern, just with the default and the "real" role swapped: here, there is no real provider to
 * default away from, so this explicit-unavailable provider is the default rather than the mock one.
 */
@Component
@ConditionalOnProperty(name = "railway-disruption.provider", havingValue = "unavailable", matchIfMissing = true)
public class UnavailableRailwayDisruptionProvider implements RailwayDisruptionProvider {

    @Override
    public RailwayDisruptionQueryResult getDisruptions(String trainNumber, RouteSection section, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        return new RailwayDisruptionQueryResult(
                trainNumber, section.fromStation().code(), section.toStation().code(),
                RailwayDisruptionAvailability.UNAVAILABLE, List.of());
    }
}
