package com.railpredictor.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code railradar.*} configuration keys (see application.properties). Never hardcode
 * {@code apiKey} - it must come from the {@code RAILRADAR_API_KEY} environment variable.
 */
@ConfigurationProperties(prefix = "railradar")
public record RailRadarProperties(String baseUrl, String apiKey, Duration timeout) {
}
