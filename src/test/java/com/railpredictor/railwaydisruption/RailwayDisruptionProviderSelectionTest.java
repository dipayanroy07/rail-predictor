package com.railpredictor.railwaydisruption;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.RailwayDisruptionMockProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the deterministic {@code railway-disruption.provider} selection rule (Phase 18,
 * docs/architecture.md): exactly one {@link RailwayDisruptionProvider} bean ever exists, chosen by
 * an explicit property, defaulting to {@code unavailable} (not {@code mock} - there is no real
 * provider to eventually default away from yet) - never both, never neither, never a silent
 * fallback. Mirrors {@code HistoricalDelayProviderSelectionTest}/{@code WeatherProviderSelectionTest}'s
 * exact structure.
 */
class RailwayDisruptionProviderSelectionTest {

    @Configuration
    static class SupportBeans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        RailwayDisruptionMockProperties railwayDisruptionMockProperties() {
            return new RailwayDisruptionMockProperties(false, null, null, null, null, null, null);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SupportBeans.class, UnavailableRailwayDisruptionProvider.class, MockRailwayDisruptionProvider.class);

    @Test
    void defaultsToUnavailableWhenThePropertyIsUnset() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RailwayDisruptionProvider.class);
            assertThat(context).hasSingleBean(UnavailableRailwayDisruptionProvider.class);
            assertThat(context).doesNotHaveBean(MockRailwayDisruptionProvider.class);
        });
    }

    @Test
    void usesUnavailableWhenExplicitlySet() {
        runner.withPropertyValues("railway-disruption.provider=unavailable").run(context -> {
            assertThat(context).hasSingleBean(RailwayDisruptionProvider.class);
            assertThat(context).hasSingleBean(UnavailableRailwayDisruptionProvider.class);
            assertThat(context).doesNotHaveBean(MockRailwayDisruptionProvider.class);
        });
    }

    @Test
    void usesMockWhenExplicitlySet() {
        runner.withPropertyValues("railway-disruption.provider=mock").run(context -> {
            assertThat(context).hasSingleBean(RailwayDisruptionProvider.class);
            assertThat(context).hasSingleBean(MockRailwayDisruptionProvider.class);
            assertThat(context).doesNotHaveBean(UnavailableRailwayDisruptionProvider.class);
        });
    }

    @Test
    void anUnrecognisedValueSelectsNeitherProviderRatherThanGuessing() {
        runner.withPropertyValues("railway-disruption.provider=crisapi").run(context -> {
            assertThat(context).doesNotHaveBean(UnavailableRailwayDisruptionProvider.class);
            assertThat(context).doesNotHaveBean(MockRailwayDisruptionProvider.class);
        });
    }
}
