package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers cascade-simulation configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(CascadeProperties.class)
public class CascadeConfig {
}
