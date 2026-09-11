package com.railpredictor.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single, injectable {@link Clock} bean, so anything that needs "now" (e.g. the mock historical
 * delay provider's day-of-week/month lookup) can be given a fixed clock in tests instead of
 * depending on the real system clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
