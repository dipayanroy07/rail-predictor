package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.HistoricalSectionMockProperties;
import com.railpredictor.config.HistoricalSectionProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import com.railpredictor.model.domain.Station;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MockSectionHistoricalDelayProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);

    private static RouteSection section() {
        return new RouteSection(new Station("NDLS", "New Delhi"), new Station("KOTA", "Kota Jn"), null);
    }

    @Test
    void returnsTheConfiguredFixedStatisticTaggedMock() {
        MockSectionHistoricalDelayProvider provider = new MockSectionHistoricalDelayProvider(
                new HistoricalSectionMockProperties(-2.0, -1.0, 3.0, 20),
                new HistoricalSectionProperties(10),
                FIXED_CLOCK);

        SectionHistoricalDelayResult result = provider.getSectionDelay("12952", section(), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.AVAILABLE);
        assertThat(result.profile().source()).isEqualTo(DataProvenance.MOCK);
        assertThat(result.profile().averageDelayChangeMinutes()).isEqualTo(-2.0);
        assertThat(result.profile().sampleCount()).isEqualTo(20);
    }

    @Test
    void reportsInsufficientSamplesWhenTheConfiguredCountIsBelowTheThreshold() {
        MockSectionHistoricalDelayProvider provider = new MockSectionHistoricalDelayProvider(
                new HistoricalSectionMockProperties(0, 0, 0, 0),
                new HistoricalSectionProperties(10),
                FIXED_CLOCK);

        SectionHistoricalDelayResult result = provider.getSectionDelay("12952", section(), Instant.now(FIXED_CLOCK));

        assertThat(result.status()).isEqualTo(SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES);
    }

    @Test
    void ignoresTheReferenceInstantEntirely() {
        MockSectionHistoricalDelayProvider provider = new MockSectionHistoricalDelayProvider(
                new HistoricalSectionMockProperties(5.0, 5.0, 0, 20),
                new HistoricalSectionProperties(10),
                FIXED_CLOCK);

        SectionHistoricalDelayResult past = provider.getSectionDelay("12952", section(), Instant.parse("2000-01-01T00:00:00Z"));
        SectionHistoricalDelayResult future = provider.getSectionDelay("12952", section(), Instant.parse("2099-01-01T00:00:00Z"));

        assertThat(past.profile().averageDelayChangeMinutes()).isEqualTo(future.profile().averageDelayChangeMinutes());
    }
}
