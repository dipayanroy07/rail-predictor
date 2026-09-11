package com.railpredictor.historical;

import com.railpredictor.config.HistoricalMockProperties;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.RouteSection;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Returns the same configured statistics for every train/section, dated to today (via the
 * injected {@link Clock}). Stands in until real historical data is available.
 *
 * <p>Active whenever {@code historical.provider} is {@code mock} or unset (the default) - see
 * {@link PostgresHistoricalDelayProvider} for the real alternative and
 * docs/historical-data-design.md for the provider-selection rules. Exactly one
 * {@link HistoricalDelayProvider} bean ever exists; the two are mutually exclusive by
 * construction, not by convention.
 */
@Component
@ConditionalOnProperty(name = "historical.provider", havingValue = "mock", matchIfMissing = true)
public class MockHistoricalDelayProvider implements HistoricalDelayProvider {

    private final HistoricalMockProperties properties;
    private final Clock clock;

    public MockHistoricalDelayProvider(HistoricalMockProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public HistoricalDelay getHistoricalDelay(String trainNumber, RouteSection section) {
        LocalDate today = LocalDate.now(clock);
        return new HistoricalDelay(
                trainNumber,
                section,
                today.getDayOfWeek(),
                today.getMonth(),
                properties.timePeriod(),
                properties.averageDelayMinutes(),
                properties.medianDelayMinutes(),
                properties.standardDeviationMinutes(),
                properties.sampleCount(),
                DataProvenance.MOCK);
    }
}
