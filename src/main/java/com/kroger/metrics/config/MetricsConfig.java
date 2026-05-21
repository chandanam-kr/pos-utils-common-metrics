package com.kroger.metrics.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MetricsConfig
{

    private static final List<String> ALLOWED_METRICS = List.of(
            "jvm.memory",
            "jvm.threads",
            "http.server",
            "process.cpu",
            "system.cpu",
            "logback",
            "business.day",
            "order",
            "payment"
    );

    @Bean
    public MeterFilter meterFilter()
    {
        return new MeterFilter()
        {
            @Override
            public MeterFilterReply accept(Meter.Id id)
            {
                boolean isAllowed = ALLOWED_METRICS.stream().anyMatch(id.getName()::startsWith);

                return isAllowed ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY;
            }
        };
    }
}