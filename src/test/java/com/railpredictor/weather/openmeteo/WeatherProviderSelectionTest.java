package com.railpredictor.weather.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.railpredictor.config.OpenMeteoProperties;
import com.railpredictor.config.WeatherMockProperties;
import com.railpredictor.model.enums.WeatherCondition;
import com.railpredictor.weather.MockWeatherProvider;
import com.railpredictor.weather.WeatherProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Verifies the deterministic {@code weather.provider} selection rule (Phase 17,
 * docs/configuration.md): exactly one {@link WeatherProvider} bean ever exists, chosen by an
 * explicit property, defaulting to mock - never both, never neither, never a silent fallback.
 * Mirrors {@code HistoricalDelayProviderSelectionTest}'s exact structure. Lives in this package
 * (rather than {@code com.railpredictor.weather}) because {@link OpenMeteoClient}/
 * {@link OpenMeteoWeatherMapper} are package-private, exactly like every other adapter's internal
 * collaborators in this codebase.
 */
class WeatherProviderSelectionTest {

    /** Stand-ins for the beans each provider needs, so both are constructible regardless of which
     * one the property actually selects. */
    @Configuration
    static class SupportBeans {
        @Bean
        WeatherMockProperties weatherMockProperties() {
            return new WeatherMockProperties(WeatherCondition.CLEAR, 25.0, 10000.0, 0.0);
        }

        @Bean
        OpenMeteoProperties openMeteoProperties() {
            return new OpenMeteoProperties("https://api.open-meteo.com", "", Duration.ofSeconds(5), 200.0);
        }

        @Bean
        WebClient openMeteoWebClient() {
            return mock(WebClient.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SupportBeans.class, MockWeatherProvider.class,
                    OpenMeteoClient.class, OpenMeteoWeatherMapper.class, OpenMeteoWeatherProvider.class);

    @Test
    void defaultsToMockWhenThePropertyIsUnset() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(WeatherProvider.class);
            assertThat(context).hasSingleBean(MockWeatherProvider.class);
            assertThat(context).doesNotHaveBean(OpenMeteoWeatherProvider.class);
        });
    }

    @Test
    void usesMockWhenExplicitlySetToMock() {
        runner.withPropertyValues("weather.provider=mock").run(context -> {
            assertThat(context).hasSingleBean(WeatherProvider.class);
            assertThat(context).hasSingleBean(MockWeatherProvider.class);
            assertThat(context).doesNotHaveBean(OpenMeteoWeatherProvider.class);
        });
    }

    @Test
    void usesOpenMeteoWhenExplicitlySetToOpenmeteo() {
        runner.withPropertyValues("weather.provider=openmeteo").run(context -> {
            assertThat(context).hasSingleBean(WeatherProvider.class);
            assertThat(context).hasSingleBean(OpenMeteoWeatherProvider.class);
            assertThat(context).doesNotHaveBean(MockWeatherProvider.class);
        });
    }

    @Test
    void anUnrecognisedValueSelectsNeitherProviderRatherThanGuessing() {
        // Neither @ConditionalOnProperty matches "accuweather" - no silent fallback to either side.
        runner.withPropertyValues("weather.provider=accuweather").run(context -> {
            assertThat(context).doesNotHaveBean(MockWeatherProvider.class);
            assertThat(context).doesNotHaveBean(OpenMeteoWeatherProvider.class);
        });
    }
}
