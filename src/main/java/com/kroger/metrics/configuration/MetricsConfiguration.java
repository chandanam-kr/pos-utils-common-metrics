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
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "metrics")
public class MetricsConfiguration
{
    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_TYPE = "custom";

    /**
     * When true, all metrics pass through with no filtering.
     */
    private boolean unfiltered = false;

    /**
     * Simple categories — category name acts as both prefix and type tag value.
     */
    private List<String> categories = new ArrayList<>();

    /**
     * Categories with explicit prefix overrides.
     */
    private Map<String, List<String>> customCategories = new LinkedHashMap<>();

    /**
     * Computed at startup.
     */
    private List<String> effectivePrefixes = new ArrayList<>();
    private Map<String, String> prefixToCategory = new LinkedHashMap<>();

    @PostConstruct
    public void init()
    {
        if (unfiltered)
        {
            log.debug(MetricsConstants.LOG_METRICS_UNFILTERED);
            return;
        }

        customCategories.forEach((categoryName, prefixes) -> {
            if (prefixes == null || prefixes.isEmpty())
            {
                log.debug(MetricsConstants.LOG_CATEGORY_NO_PREFIXES, categoryName);
                return;
            }

            prefixes.forEach(prefix -> {
                effectivePrefixes.add(prefix);
                prefixToCategory.put(prefix, categoryName);
            });
        });

        categories.stream()
                .filter(category -> !customCategories.containsKey(category))
                .forEach(category -> {
                    effectivePrefixes.add(category);
                    prefixToCategory.put(category, category);
                });

        log.info(
                MetricsConstants.LOG_METRICS_FILTER_INIT,
                allCategoryNames(),
                effectivePrefixes,
                customCategories.keySet()
        );

        if (effectivePrefixes.isEmpty())
        {
            log.debug(MetricsConstants.LOG_NO_PREFIXES_CONFIGURED);
        }
    }

    private List<String> allCategoryNames()
    {
        List<String> all = new ArrayList<>(customCategories.keySet());
        categories.stream()
                .filter(category -> !customCategories.containsKey(category))
                .forEach(all::add);
        return all;
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${info.app.name:}") String appName)
    {
        String resolvedApp = (appName != null && !appName.isBlank()) ? appName : UNKNOWN;

        log.info(MetricsConstants.LOG_COMMON_TAGS_INIT, resolvedApp);

        if (UNKNOWN.equals(resolvedApp))
        {
            log.warn(MetricsConstants.LOG_APP_TAG_UNKNOWN);
        }

        return registry -> registry.config().commonTags(Tags.of("app", resolvedApp));
    }

    @Bean
    public MeterFilter metricsMeterFilter()
    {
        return new MeterFilter()
        {
            @Override
            public MeterFilterReply accept(Meter.Id id)
            {
                try
                {
                    if (unfiltered)
                    {
                        return MeterFilterReply.NEUTRAL;
                    }

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

            @Override
            public Meter.Id map(Meter.Id id)
            {
                try
                {
                    String type = resolveType(id.getName());
                    return id.withTags(Tags.of("type", type));
                }
                catch (Exception e)
                {
                    log.warn("Failed to resolve metric type for {}", id.getName(), e);
                    return id.withTags(Tags.of("type", DEFAULT_TYPE));
                }
            }
        };
    }

    private boolean isAllowed(String metricName)
    {
        if (metricName == null || metricName.isBlank())
        {
            return false;
        }

        String dottedName = metricName.replace('_', '.');
        return effectivePrefixes.stream().anyMatch(dottedName::startsWith);
    }

    private String resolveType(String metricName)
    {
        if (metricName == null || metricName.isBlank())
        {
            return DEFAULT_TYPE;
        }

        String dotted = metricName.replace('_', '.');

        return prefixToCategory.entrySet().stream()
                .filter(entry -> dotted.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> deriveTypeFromFirstSegment(dotted));
    }

    private String deriveTypeFromFirstSegment(String dotted)
    {
        int firstDot = dotted.indexOf('.');
        return firstDot > 0 ? dotted.substring(0, firstDot) : DEFAULT_TYPE;
    }
}