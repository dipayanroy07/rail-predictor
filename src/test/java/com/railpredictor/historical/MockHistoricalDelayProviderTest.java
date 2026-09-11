package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.config.HistoricalMockProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.Station;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MockHistoricalDelayProviderTest {

    // 2026-09-09 is a Wednesday in September.
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
    private final RouteSection section =
            new RouteSection(new Station("NDLS", "New Delhi"), new Station("GZB", "Ghaziabad"), null);

    @Test
    void datesTheResultToTodayAccordingToTheInjectedClock() {
        MockHistoricalDelayProvider provider = new MockHistoricalDelayProvider(
                new HistoricalMockProperties(8.0, 6.0, 3.0, 50, "MORNING_PEAK"), fixedClock);

        HistoricalDelay result = provider.getHistoricalDelay("12345", section);

        assertThat(result.trainNumber()).isEqualTo("12345");
        assertThat(result.section()).isEqualTo(section);
        assertThat(result.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(result.month()).isEqualTo(Month.SEPTEMBER);
        assertThat(result.timePeriod()).isEqualTo("MORNING_PEAK");
        assertThat(result.averageDelayMinutes()).isEqualTo(8.0);
        assertThat(result.medianDelayMinutes()).isEqualTo(6.0);
        assertThat(result.standardDeviationMinutes()).isEqualTo(3.0);
        assertThat(result.sampleCount()).isEqualTo(50);
        assertThat(result.source()).isEqualTo(DataProvenance.MOCK);
    }

    @Test
    void defaultsRepresentNoHistoricalData() {
        MockHistoricalDelayProvider provider = new MockHistoricalDelayProvider(
                new HistoricalMockProperties(0, 0, 0, 0, null), fixedClock);

        assertThat(provider.getHistoricalDelay("12345", section).sampleCount()).isZero();
    }

    @Test
    void rejectsBlankTrainNumberViaTheDomainRecordsOwnValidation() {
        MockHistoricalDelayProvider provider = new MockHistoricalDelayProvider(
                new HistoricalMockProperties(0, 0, 0, 0, null), fixedClock);

        assertThrows(IllegalArgumentException.class, () -> provider.getHistoricalDelay(" ", section));
    }
}
