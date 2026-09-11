package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@code railway-disruption.mock.*} (Phase 18). Mirrors {@code WeatherConfig}'s role
 * for {@code weather.mock.*}. */
@Configuration
@EnableConfigurationProperties(RailwayDisruptionMockProperties.class)
public class RailwayDisruptionConfig {
}
