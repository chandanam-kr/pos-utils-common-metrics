package com.kroger.metrics.configuration;

import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static com.kroger.metrics.constants.MetricsConstants.DEFAULT_ALLOWED;

/**
 * Configuration for Prometheus meter filtering.
 * Allows system metrics by default and lets user add business specific prefixes.
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "metrics")
public class MetricsConfiguration
{

    /**
     * User defined business metric prefixes from application.yml.
     */
    private List<String> additionalPrefixes = new ArrayList<>();

    public List<String> getAdditionalPrefixes()
    {
        return additionalPrefixes;
    }

    public void setAdditionalPrefixes(List<String> additionalPrefixes)
    {
        this.additionalPrefixes = additionalPrefixes != null
                ? additionalPrefixes
                : new ArrayList<>();
    }

    /**
     * Logs loaded configuration at startup for debugging.
     */
    @PostConstruct
    public void logConfig()
    {
        log.info(MetricsConstants.LOG_METRICS_FILTER_INIT, DEFAULT_ALLOWED, additionalPrefixes);
    }

    /**
     * Creates MeterFilter that allows metrics matching default or user prefixes.
     * On any error defaults to NEUTRAL to avoid blocking metrics.
     */
    @Bean
    public MeterFilter meterFilter()
    {
        return new MeterFilter()
        {
            @Override
            public MeterFilterReply accept(Meter.Id id)
            {
                try
                {
                    return isAllowed(id.getName()) ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY;
                }
                catch (Exception e)
                {
                    log.warn(MetricsConstants.LOG_METER_FILTER_ERROR, id.getName(), e.getMessage());
                    return MeterFilterReply.NEUTRAL;
                }
            }
        };
    }

    /**
     * Checks if metric name starts with any allowed prefix.
     * Returns false for null or empty metric names.
     */
    private boolean isAllowed(String metricName)
    {
        if (metricName == null || metricName.isBlank()) return false;

        return DEFAULT_ALLOWED.stream().anyMatch(metricName::startsWith)
                || additionalPrefixes.stream().anyMatch(metricName::startsWith);
    }
}