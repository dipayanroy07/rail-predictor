package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers disruption-simulation configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(DisruptionProperties.class)
public class DisruptionConfig {
}
