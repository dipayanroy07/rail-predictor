package com.railpredictor.railwaydisruption;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.RailwayDisruptionAvailability;
import com.railpredictor.model.domain.RailwayDisruptionQueryResult;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UnavailableRailwayDisruptionProviderTest {

    private final UnavailableRailwayDisruptionProvider provider = new UnavailableRailwayDisruptionProvider();

    @Test
    void alwaysReportsUnavailableNeverNoKnownDisruption() {
        RouteSection section = new RouteSection(
                new Station("KOTA", "Kota Jn"), new Station("RTM", "Ratlam Jn"), 465.0);

        RailwayDisruptionQueryResult result = provider.getDisruptions("12952", section, Instant.now());

        assertThat(result.availability()).isEqualTo(RailwayDisruptionAvailability.UNAVAILABLE);
        assertThat(result.disruptions()).isEmpty();
        assertThat(result.trainNumber()).isEqualTo("12952");
        assertThat(result.fromStationCode()).isEqualTo("KOTA");
        assertThat(result.toStationCode()).isEqualTo("RTM");
    }

    @Test
    void behavesIdenticallyRegardlessOfWhichTrainOrSectionIsQueried() {
        RouteSection section = new RouteSection(
                new Station("NDLS", "New Delhi"), new Station("BCT", "Mumbai Central"), 1384.0);

        RailwayDisruptionQueryResult result = provider.getDisruptions("99999", section, Instant.now());

        assertThat(result.availability()).isEqualTo(RailwayDisruptionAvailability.UNAVAILABLE);
    }
}
