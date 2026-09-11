package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers confidence-scoring configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(ConfidenceProperties.class)
public class ConfidenceConfig {
}
