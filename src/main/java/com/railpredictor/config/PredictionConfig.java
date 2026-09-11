package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers prediction-engine configuration properties as Spring beans. */
@Configuration
@EnableConfigurationProperties({TravelTimeProperties.class, HistoricalAdjustmentProperties.class})
public class PredictionConfig {
}
