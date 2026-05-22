package com.kroger.metrics.controller;

import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes Prometheus metrics at /metrics endpoint
 * with custom transformations like renaming, type and timestamp tags.
 */
@RestController
public class MetricsController
{
    private static final String DEFAULT_TYPE = "custom";

    private final PrometheusMeterRegistry registry;

    public MetricsController(PrometheusMeterRegistry registry)
    {
        this.registry = registry;
    }

    /**
     * Returns clean Prometheus formatted metrics.
     * Removes comments, renames metrics and tags,
     * adds type and timestamp tags to every line.
     */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics()
    {
        String timestamp = Instant.now().toString();

        return Arrays.stream(registry.scrape().split("\n"))
                .filter(this::isValidLine)
                .map(this::renameMetric)
                .map(this::renameTagValues)
                .map(line -> enrichWithTypeAndTimestamp(line, timestamp))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Filters out comment lines and blank lines(#).
     */
    private boolean isValidLine(String line)
    {
        return !line.startsWith("#") && !line.isBlank();
    }

    /**
     * Renames metric names based on the predefined mapping.
     * Returns line unchanged if no mapping found.
     */
    private String renameMetric(String line)
    {
        int endIndex = findMetricNameEnd(line);
        if (endIndex == -1) return line;

        String metricName = line.substring(0, endIndex);
        String renamed    = MetricsConstants.METRIC_NAME_RENAMES
                .getOrDefault(metricName, metricName);

        return renamed + line.substring(endIndex);
    }

    /**
     * Replaces tag values based on the predefined mapping.
     */
    private String renameTagValues(String line)
    {
        for (Map.Entry<String, String> entry : MetricsConstants.TAG_VALUE_RENAMES.entrySet())
            line = line.replace("=\"" + entry.getKey() + "\"",
                    "=\"" + entry.getValue() + "\"");

        return line;
    }

    /**
     * Adds type and timestamp tags to every metric line.
     * Handles lines with and without existing tag blocks.
     */
    private String enrichWithTypeAndTimestamp(String line, String timestamp)
    {
        String type = resolveType(line);
        String extraTags = buildExtraTags(type, timestamp);

        return line.contains("{") ? injectIntoExistingTags(line, extraTags) : appendNewTagBlock(line, extraTags);
    }

    /**
     * Resolves type by looking up original metric name in METRIC_TYPE map.
     * Returns "custom" if not found (for user defined metrics).
     */
    private String resolveType(String line)
    {
        int endIndex = findMetricNameEnd(line);
        if (endIndex == -1) return DEFAULT_TYPE;

        String renamedMetric  = line.substring(0, endIndex);
        String originalMetric = findOriginalMetricName(renamedMetric);

        return MetricsConstants.METRIC_TYPE.getOrDefault(originalMetric, DEFAULT_TYPE);
    }

    /**
     * Finds original metric name by reverse lookup in rename map.
     */
    private String findOriginalMetricName(String renamedMetric)
    {
        return MetricsConstants.METRIC_NAME_RENAMES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(renamedMetric))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(renamedMetric);
    }

    /**
     * Builds the type and timestamp tag string.
     */
    private String buildExtraTags(String type, String timestamp)
    {
        return ",type=\"" + type + "\",timestamp=\"" + timestamp + "\"";
    }

    /**
     * Injects extra tags into existing tag block before closing brace.
     * Example: metric{a="1"} 1.0 becomes metric{a="1",type="x",timestamp="y"} 1.0
     */
    private String injectIntoExistingTags(String line, String extraTags)
    {
        int lastBrace = line.lastIndexOf("}");
        return line.substring(0, lastBrace) + extraTags + "}" + line.substring(lastBrace + 1);
    }

    /**
     * Appends new tag block to a metric line that has no tags.
     * Example: metric 1.0 becomes metric{type="x",timestamp="y"} 1.0
     */
    private String appendNewTagBlock(String line, String extraTags)
    {
        int spaceIndex = line.lastIndexOf(" ");
        String metricName = line.substring(0, spaceIndex);
        String value = line.substring(spaceIndex);
        String tags = extraTags.startsWith(",") ? extraTags.substring(1) : extraTags;

        return metricName + "{" + tags + "}" + value;
    }

    /**
     * Finds where metric name ends - either at { or space.
     */
    private int findMetricNameEnd(String line)
    {
        return line.contains("{") ? line.indexOf("{") : line.indexOf(" ");
    }
}