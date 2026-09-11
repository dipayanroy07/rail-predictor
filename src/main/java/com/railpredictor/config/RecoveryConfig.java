package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers recovery-simulation configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(RecoveryProperties.class)
public class RecoveryConfig {
}
