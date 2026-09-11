package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers weather configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(WeatherMockProperties.class)
public class WeatherConfig {
}
