package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/** Wires up the HTTP client used to call RailRadar. */
@Configuration
@EnableConfigurationProperties(RailRadarProperties.class)
public class RailRadarConfig {

    @Bean
    WebClient railRadarWebClient(WebClient.Builder builder, RailRadarProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }
}
