package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers prediction-evaluation configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(PredictionEvaluationProperties.class)
public class PredictionEvaluationConfig {
}
