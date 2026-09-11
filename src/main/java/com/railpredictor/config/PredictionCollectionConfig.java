package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers prediction-collection configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(PredictionCollectionProperties.class)
public class PredictionCollectionConfig {
}
