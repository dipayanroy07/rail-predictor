package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires up the HTTP client used to call Open-Meteo (https://open-meteo.com/en/docs). Unlike
 * {@link RailRadarConfig}, no default {@code Authorization} header is set - Open-Meteo's
 * non-commercial tier (this application's default use case) requires no API key at all; a
 * configured key, if any, is appended as an {@code apikey} query parameter by
 * {@code OpenMeteoClient} itself (Open-Meteo's own convention for its paid tier), not sent as a
 * header.
 */
@Configuration
@EnableConfigurationProperties(OpenMeteoProperties.class)
public class OpenMeteoConfig {

    @Bean
    WebClient openMeteoWebClient(WebClient.Builder builder, OpenMeteoProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
