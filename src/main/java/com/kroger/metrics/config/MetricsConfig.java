package com.kroger.metrics.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Prometheus meter filtering.
 * Allows system metrics by default and lets user add business specific prefixes.
 */
@Configuration
@ConfigurationProperties(prefix = "metrics")
public class MetricsConfig
{
    /**
     * Library default prefixes for common system metrics.
     * Always allowed regardless of user configuration.
     */
    private static final List<String> DEFAULT_ALLOWED = List.of(
            "jvm.memory",
            "jvm.threads",
            "http.server",
            "process.cpu",
            "system.cpu",
            "logback"
    );

    /**
     * User defined business metric prefixes.
     * Configured via metrics.additional-prefixes in application.yml
     */
    private List<String> additionalPrefixes = new ArrayList<>();

    public List<String> getAdditionalPrefixes()
    {
        return additionalPrefixes;
    }

    public void setAdditionalPrefixes(List<String> additionalPrefixes)
    {
        this.additionalPrefixes = additionalPrefixes;
    }

    /**
     * MeterFilter bean that allows metrics matching default or user defined prefixes.
     * Denies all other metrics.
     */
    @Bean
    public MeterFilter meterFilter()
    {
        return new MeterFilter()
        {
            @Override
            public MeterFilterReply accept(Meter.Id id)
            {
                String name = id.getName();

                boolean isAllowed = DEFAULT_ALLOWED.stream().anyMatch(name::startsWith)
                        || additionalPrefixes.stream().anyMatch(name::startsWith);

                return isAllowed
                        ? MeterFilterReply.NEUTRAL
                        : MeterFilterReply.DENY;
            }
        };
    }
}