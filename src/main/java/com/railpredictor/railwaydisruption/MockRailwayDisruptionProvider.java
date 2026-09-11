package com.railpredictor.railwaydisruption;

import com.railpredictor.config.RailwayDisruptionMockProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionAvailability;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RouteSection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Returns a single, deterministically-configured disruption (or none) for every query, tagged
 * {@code source = "mock-provider"} so downstream code never mistakes it for a real operational
 * report. Stands in for local testing/demonstration only - see
 * {@link UnavailableRailwayDisruptionProvider}'s own Javadoc for why this is explicitly {@code not}
 * the production default the way {@code MockWeatherProvider}/{@code MockHistoricalDelayProvider}
 * are for their own domains (no real alternative exists yet to eventually replace this one).
 *
 * <p>Active whenever {@code railway-disruption.provider=mock} is explicitly set - never the
 * default (see {@link UnavailableRailwayDisruptionProvider}). Matching mirrors
 * {@link RailwayDisruptionProvider#getDisruptions}'s own contract: the configured disruption
 * applies when its {@code fromStationCode}/{@code toStationCode} match the queried section exactly,
 * and either its own {@code trainNumber} is blank (route-wide - matches any queried train) or
 * equals the queried {@code trainNumber} exactly.
 *
 * <p>When it applies, it is always reported {@link com.railpredictor.model.domain.RailwayDisruptionStatus#ACTIVE}
 * as of "now" - {@code effectiveFrom} is stamped an hour before the injected {@link Clock}'s
 * current instant, {@code effectiveUntil} is left open-ended (still ongoing).
 */
@Component
@ConditionalOnProperty(name = "railway-disruption.provider", havingValue = "mock")
public class MockRailwayDisruptionProvider implements RailwayDisruptionProvider {

    private final RailwayDisruptionMockProperties properties;
    private final Clock clock;

    public MockRailwayDisruptionProvider(RailwayDisruptionMockProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RailwayDisruptionQueryResult getDisruptions(String trainNumber, RouteSection section, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        String fromStationCode = section.fromStation().code();
        String toStationCode = section.toStation().code();

        List<RailwayDisruption> disruptions = matches(trainNumber, fromStationCode, toStationCode)
                ? List.of(configuredDisruption())
                : List.of();

        return new RailwayDisruptionQueryResult(
                trainNumber, fromStationCode, toStationCode, RailwayDisruptionAvailability.AVAILABLE, disruptions);
    }

    private boolean matches(String trainNumber, String fromStationCode, String toStationCode) {
        if (!properties.enabled()) {
            return false;
        }
        boolean trainMatches = properties.trainNumber() == null || properties.trainNumber().equals(trainNumber);
        return trainMatches
                && properties.fromStationCode().equals(fromStationCode)
                && properties.toStationCode().equals(toStationCode);
    }

    private RailwayDisruption configuredDisruption() {
        Instant now = Instant.now(clock);
        return new RailwayDisruption(
                properties.type(),
                properties.trainNumber(),
                properties.fromStationCode(),
                properties.toStationCode(),
                properties.restrictedSpeedKmh(),
                properties.severity(),
                now.minus(Duration.ofHours(1)),
                null,
                now,
                DataProvenance.MOCK,
                null);
    }
}
