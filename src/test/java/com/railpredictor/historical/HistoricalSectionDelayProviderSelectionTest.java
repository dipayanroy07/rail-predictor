package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.railpredictor.config.HistoricalSectionMockProperties;
import com.railpredictor.config.HistoricalSectionProperties;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.HistoricalSectionDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalSectionDelayProfileRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the deterministic {@code historical.section-provider} selection rule (Phase 16G) -
 * exactly one {@link HistoricalSectionDelayProvider} bean ever exists, chosen by an explicit
 * property, defaulting to mock - never both, never neither, never a silent fallback. Mirrors
 * {@link HistoricalDelayProviderSelectionTest}'s exact pattern for the separate, unrelated
 * station-level selection property.
 */
class HistoricalSectionDelayProviderSelectionTest {

    @Configuration
    static class SupportBeans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        HistoricalSectionMockProperties historicalSectionMockProperties() {
            return new HistoricalSectionMockProperties(0, 0, 0, 0);
        }

        @Bean
        HistoricalSectionProperties historicalSectionProperties() {
            return new HistoricalSectionProperties(10);
        }

        @Bean
        HistoricalSectionDelayProfileRepository historicalSectionDelayProfileRepository() {
            return mock(HistoricalSectionDelayProfileRepository.class);
        }

        @Bean
        HistoricalSectionDelayProfileEntityMapper historicalSectionDelayProfileEntityMapper() {
            return new HistoricalSectionDelayProfileEntityMapper();
        }

        @Bean
        HistoricalObservationRepository historicalObservationRepository() {
            return mock(HistoricalObservationRepository.class);
        }

        @Bean
        HistoricalObservationEntityMapper historicalObservationEntityMapper() {
            return new HistoricalObservationEntityMapper();
        }

        @Bean
        HistoricalSectionDelayProfileAggregator historicalSectionDelayProfileAggregator(Clock clock) {
            return new HistoricalSectionDelayProfileAggregator(clock);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(
            SupportBeans.class, MockSectionHistoricalDelayProvider.class, PostgresSectionHistoricalDelayProvider.class);

    @Test
    void defaultsToMockWhenThePropertyIsUnset() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HistoricalSectionDelayProvider.class);
            assertThat(context).hasSingleBean(MockSectionHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresSectionHistoricalDelayProvider.class);
        });
    }

    @Test
    void usesMockWhenExplicitlySetToMock() {
        runner.withPropertyValues("historical.section-provider=mock").run(context -> {
            assertThat(context).hasSingleBean(HistoricalSectionDelayProvider.class);
            assertThat(context).hasSingleBean(MockSectionHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresSectionHistoricalDelayProvider.class);
        });
    }

    @Test
    void usesPostgresWhenExplicitlySetToPostgresAndItsDependenciesAreAvailable() {
        runner.withPropertyValues("historical.section-provider=postgres").run(context -> {
            assertThat(context).hasSingleBean(HistoricalSectionDelayProvider.class);
            assertThat(context).hasSingleBean(PostgresSectionHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(MockSectionHistoricalDelayProvider.class);
        });
    }

    @Test
    void anUnrecognisedValueSelectsNeitherProviderRatherThanGuessing() {
        runner.withPropertyValues("historical.section-provider=railradar").run(context -> {
            assertThat(context).doesNotHaveBean(MockSectionHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresSectionHistoricalDelayProvider.class);
        });
    }

    @Test
    void isIndependentFromTheStationLevelProviderSelectionProperty() {
        // historical.provider (station-level) must have no bearing on historical.section-provider.
        runner.withPropertyValues("historical.provider=postgres", "historical.section-provider=mock").run(context -> {
            assertThat(context).hasSingleBean(MockSectionHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresSectionHistoricalDelayProvider.class);
        });
    }
}
