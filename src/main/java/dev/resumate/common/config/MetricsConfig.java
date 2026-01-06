package dev.resumate.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter outboxDuplicateAttemptCounter(MeterRegistry meterRegistry) {
        return Counter.builder("outbox.race.condition")
                .description("Duplicate outbox publish attempts")
                .register(meterRegistry);
    }
}
