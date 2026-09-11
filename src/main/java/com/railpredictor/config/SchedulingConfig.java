package com.railpredictor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables Spring's {@code @Scheduled} processing - required by
 * {@code HistoricalDelayProfileRefreshScheduler} (Phase 16C). No other scheduled task exists yet. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
