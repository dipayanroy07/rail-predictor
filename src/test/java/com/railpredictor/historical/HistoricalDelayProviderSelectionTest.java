package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.railpredictor.config.HistoricalMockProperties;
import com.railpredictor.repository.HistoricalDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalDelayProfileRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the deterministic {@code historical.provider} selection rule (item 8,
 * docs/historical-data-design.md): exactly one {@link HistoricalDelayProvider} bean ever exists,
 * chosen by an explicit property, defaulting to mock - never both, never neither, never a silent
 * fallback.
 */
class HistoricalDelayProviderSelectionTest {

    /** Stand-ins for the beans each provider needs, so both are constructible regardless of
     * which one the property actually selects - isolates this test to the selection rule itself. */
    @Configuration
    static class SupportBeans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        HistoricalMockProperties historicalMockProperties() {
            return new HistoricalMockProperties(0, 0, 0, 0, null);
        }

        @Bean
        HistoricalDelayProfileRepository historicalDelayProfileRepository() {
            return mock(HistoricalDelayProfileRepository.class);
        }

        @Bean
        HistoricalDelayProfileEntityMapper historicalDelayProfileEntityMapper() {
            return new HistoricalDelayProfileEntityMapper();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SupportBeans.class, MockHistoricalDelayProvider.class, PostgresHistoricalDelayProvider.class);

    @Test
    void defaultsToMockWhenThePropertyIsUnset() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HistoricalDelayProvider.class);
            assertThat(context).hasSingleBean(MockHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresHistoricalDelayProvider.class);
        });
    }

    @Test
    void usesMockWhenExplicitlySetToMock() {
        runner.withPropertyValues("historical.provider=mock").run(context -> {
            assertThat(context).hasSingleBean(HistoricalDelayProvider.class);
            assertThat(context).hasSingleBean(MockHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresHistoricalDelayProvider.class);
        });
    }

    @Test
    void usesPostgresWhenExplicitlySetToPostgresAndItsDependenciesAreAvailable() {
        runner.withPropertyValues("historical.provider=postgres").run(context -> {
            assertThat(context).hasSingleBean(HistoricalDelayProvider.class);
            assertThat(context).hasSingleBean(PostgresHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(MockHistoricalDelayProvider.class);
        });
    }

    @Test
    void anUnrecognisedValueSelectsNeitherProviderRatherThanGuessing() {
        // Neither @ConditionalOnProperty matches "railradar" - no silent fallback to either side.
        runner.withPropertyValues("historical.provider=railradar").run(context -> {
            assertThat(context).doesNotHaveBean(MockHistoricalDelayProvider.class);
            assertThat(context).doesNotHaveBean(PostgresHistoricalDelayProvider.class);
        });
    }
}
