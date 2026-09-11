package com.railpredictor.historical;

import com.railpredictor.config.HistoricalSectionMockProperties;
import com.railpredictor.config.HistoricalSectionProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Returns the same configured delay-change statistics for every train/section, tagged
 * {@link DataProvenance#MOCK} - stands in until real section historical data is wired into
 * prediction (which Phase 16G deliberately does not do). Ignores {@code referenceInstant}
 * entirely - a fixed mock statistic has no notion of a point-in-time cutoff, exactly like
 * {@link MockHistoricalDelayProvider} ignores nothing-in-particular-time-based today.
 *
 * <p>Active whenever {@code historical.section-provider} is {@code mock} or unset (the default) -
 * see {@link PostgresSectionHistoricalDelayProvider} for the real alternative. This is a
 * deliberately <b>separate</b> selection property from {@code historical.provider} (the
 * station-level provider's own switch) - see docs/historical-data-design.md's Phase 16G notes.
 */
@Component
@ConditionalOnProperty(name = "historical.section-provider", havingValue = "mock", matchIfMissing = true)
public class MockSectionHistoricalDelayProvider implements HistoricalSectionDelayProvider {

    private final HistoricalSectionMockProperties mockProperties;
    private final HistoricalSectionProperties sectionProperties;
    private final Clock clock;

    public MockSectionHistoricalDelayProvider(
            HistoricalSectionMockProperties mockProperties,
            HistoricalSectionProperties sectionProperties,
            Clock clock) {
        this.mockProperties = mockProperties;
        this.sectionProperties = sectionProperties;
        this.clock = clock;
    }

    @Override
    public SectionHistoricalDelayResult getSectionDelay(String trainNumber, RouteSection section, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        String fromStationCode = section.fromStation().code();
        String toStationCode = section.toStation().code();

        HistoricalSectionDelayProfile profile = new HistoricalSectionDelayProfile(
                trainNumber, fromStationCode, toStationCode,
                mockProperties.sampleCount(),
                mockProperties.averageDelayChangeMinutes(),
                mockProperties.medianDelayChangeMinutes(),
                mockProperties.standardDeviationMinutes(),
                DataProvenance.MOCK,
                Instant.now(clock));

        SectionHistoricalDelayStatus status = profile.sampleCount() >= sectionProperties.minimumSampleCount()
                ? SectionHistoricalDelayStatus.AVAILABLE
                : SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES;

        return new SectionHistoricalDelayResult(trainNumber, fromStationCode, toStationCode, status, profile);
    }
}
