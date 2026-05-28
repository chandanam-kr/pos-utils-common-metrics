package com.kroger.metrics.controller;

import com.kroger.metrics.configuration.MetricsConfiguration;
import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes Prometheus metrics at /metrics with:
 *   - auto-derived "type" tag from YAML categories (or first metric segment in unfiltered mode)
 *   - timestamp tag for traceability
 */
@Slf4j
@RestController
public class MetricsController
{
    private static final String DEFAULT_TYPE = "custom";

    private final PrometheusMeterRegistry registry;
    private final MetricsConfiguration    metricsConfig;

    public MetricsController(PrometheusMeterRegistry registry,
                             MetricsConfiguration metricsConfig)
    {
        this.registry      = registry;
        this.metricsConfig = metricsConfig;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> metrics()
    {
        try
        {
            return ResponseEntity.ok(generateMetrics());
        }
        catch (Exception e)
        {
            log.error(MetricsConstants.LOG_METRICS_CONTROLLER_ERROR, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MetricsConstants.ERROR_GENERATING_METRICS_BODY);
        }
    }

    private String generateMetrics()
    {
        String timestamp = Instant.now().toString();
        return Arrays.stream(registry.scrape().split("\n"))
                .filter(this::isValidLine)
                .map(line -> enrichWithTypeAndTimestamp(line, timestamp))
                .collect(Collectors.joining("\n"));
    }

    private boolean isValidLine(String line)
    {
        return !line.startsWith("#") && !line.isBlank();
    }

    private String enrichWithTypeAndTimestamp(String line, String timestamp)
    {
        String extraTags = buildExtraTags(resolveType(line), timestamp);
        return line.contains("{")
                ? injectIntoExistingTags(line, extraTags)
                : appendNewTagBlock(line, extraTags);
    }

    /**
     * Type resolution:
     *   1. Match against configured category prefixes → use category name
     *   2. Fallback: use first dot-segment of the metric name (e.g., "jvm.memory.used" → "jvm")
     *   3. Final fallback: "custom"
     */
    private String resolveType(String line)
    {
        int endIndex = findMetricNameEnd(line);
        if (endIndex == -1) return DEFAULT_TYPE;

        String metricName = line.substring(0, endIndex);
        String dotted     = metricName.replace('_', '.');

        return metricsConfig.getPrefixToCategory().entrySet().stream()
                .filter(e -> dotted.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> deriveTypeFromFirstSegment(dotted));
    }

    /**
     * Extracts the first dot-separated segment as the type.
     * Examples:
     *   "jvm.memory.used"       → "jvm"
     *   "http.server.requests"  → "http"
     *   "my.business.metric"    → "my"
     *   "singletoken"           → "custom" (no dot)
     */
    private String deriveTypeFromFirstSegment(String dotted)
    {
        int firstDot = dotted.indexOf('.');
        return firstDot > 0 ? dotted.substring(0, firstDot) : DEFAULT_TYPE;
    }

    private String buildExtraTags(String type, String timestamp)
    {
        return ",type=\"" + type + "\",timestamp=\"" + timestamp + "\"";
    }

    private String injectIntoExistingTags(String line, String extraTags)
    {
        int lastBrace = line.lastIndexOf("}");
        return line.substring(0, lastBrace) + extraTags + "}" + line.substring(lastBrace + 1);
    }

    private String appendNewTagBlock(String line, String extraTags)
    {
        int spaceIndex = line.lastIndexOf(" ");
        String tags    = extraTags.startsWith(",") ? extraTags.substring(1) : extraTags;
        return line.substring(0, spaceIndex) + "{" + tags + "}" + line.substring(spaceIndex);
    }

    private int findMetricNameEnd(String line)
    {
        return line.contains("{") ? line.indexOf("{") : line.indexOf(" ");
    }
}