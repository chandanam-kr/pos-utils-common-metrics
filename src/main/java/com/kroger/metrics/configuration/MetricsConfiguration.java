package com.kroger.metrics.configuration;

import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metric filtering + global tag injection driven by application.yml.
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "metrics")
public class MetricsConfiguration
{
    private static final String UNKNOWN = "unknown";

    //When true, all metrics pass through with no filtering.
    private boolean unfiltered = false;

    //Simple categories — name acts as both prefix and type tag value.
    private List<String> categories = new ArrayList<>();

    //Categories with explicit prefix overrides.
    private Map<String, List<String>> customCategories = new LinkedHashMap<>();

    // Computed at startup
    private List<String> effectivePrefixes        = new ArrayList<>();
    private Map<String, String> prefixToCategory  = new LinkedHashMap<>();

    @PostConstruct
    public void init()
    {
        if (unfiltered)
        {
            log.debug(MetricsConstants.LOG_METRICS_UNFILTERED);
            return;
        }

        customCategories.forEach((name, prefixes) -> {
            if (prefixes == null || prefixes.isEmpty())
            {
                log.debug(MetricsConstants.LOG_CATEGORY_NO_PREFIXES, name);
                return;
            }
            prefixes.forEach(p -> {
                effectivePrefixes.add(p);
                prefixToCategory.put(p, name);
            });
        });

        categories.stream()
                .filter(name -> !customCategories.containsKey(name))
                .forEach(name -> {
                    effectivePrefixes.add(name);
                    prefixToCategory.put(name, name);
                });

        log.info(MetricsConstants.LOG_METRICS_FILTER_INIT,
                allCategoryNames(), effectivePrefixes, customCategories.keySet());

        if (effectivePrefixes.isEmpty())
            log.debug(MetricsConstants.LOG_NO_PREFIXES_CONFIGURED);
    }

    private List<String> allCategoryNames()
    {
        List<String> all = new ArrayList<>(customCategories.keySet());
        categories.stream()
                .filter(name -> !customCategories.containsKey(name))
                .forEach(all::add);
        return all;
    }

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
                    if (unfiltered) return MeterFilterReply.NEUTRAL;

                    return isAllowed(id.getName())
                            ? MeterFilterReply.NEUTRAL
                            : MeterFilterReply.DENY;
                }
                catch (Exception e)
                {
                    log.warn(MetricsConstants.LOG_METER_FILTER_ERROR, id.getName(), e.getMessage());
                    return MeterFilterReply.NEUTRAL;
                }
            }
        };
    }

    private boolean isAllowed(String metricName)
    {
        if (metricName == null || metricName.isBlank()) return false;
        return effectivePrefixes.stream().anyMatch(metricName::startsWith);
    }

    /**
     * Auto-adds the "app" tag as a common tag on every metric.
     * Value is sourced from info.app.name in application.yml.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${info.app.name:}") String appName)
    {
        String resolvedApp = (appName != null && !appName.isBlank()) ? appName : UNKNOWN;

        log.info(MetricsConstants.LOG_COMMON_TAGS_INIT, resolvedApp);

        if (UNKNOWN.equals(resolvedApp))
            log.warn(MetricsConstants.LOG_APP_TAG_UNKNOWN);

        return registry -> registry.config().commonTags(Tags.of("app", resolvedApp));
    }
}