package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers route/section-analysis configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties(SectionAnalysisProperties.class)
public class RouteConfig {
}
