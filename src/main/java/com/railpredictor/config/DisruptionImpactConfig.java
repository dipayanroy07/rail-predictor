package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@code railway-disruption-impact.*} (Phase 19). Mirrors {@code DisruptionConfig}'s
 * role for {@code simulation.disruption.*}. */
@Configuration
@EnableConfigurationProperties(DisruptionImpactProperties.class)
public class DisruptionImpactConfig {
}
