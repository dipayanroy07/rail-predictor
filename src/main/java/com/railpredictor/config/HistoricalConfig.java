package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers historical-delay configuration properties as a Spring bean. */
@Configuration
@EnableConfigurationProperties({
        HistoricalMockProperties.class,
        HistoricalObservationValidationProperties.class,
        HistoricalJourneyDateProperties.class,
        HistoricalSectionProperties.class,
        HistoricalSectionMockProperties.class,
        HistoricalCollectionProperties.class})
public class HistoricalConfig {
}
