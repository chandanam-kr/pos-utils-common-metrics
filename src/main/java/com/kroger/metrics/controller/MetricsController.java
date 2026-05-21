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

@RestController
public class MetricsController
{

    private final PrometheusMeterRegistry registry;

    public MetricsController(PrometheusMeterRegistry registry)
    {
        this.registry = registry;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        String timestamp = Instant.now().toString();

        return Arrays.stream(registry.scrape().split("\n"))
                .filter(line -> !line.startsWith("#"))
                .filter(line -> !line.isBlank())
                .map(this::renameMetricName)
                .map(this::renameTagValues)
                .map(line -> addTypeAndTimestamp(line, timestamp))
                .collect(Collectors.joining("\n"));
    }

    private String renameMetricName(String line)
    {
        int endIndex = line.contains("{") ? line.indexOf("{") : line.indexOf(" ");

        if (endIndex == -1) return line;

        String metricName = line.substring(0, endIndex);
        String rest       = line.substring(endIndex);
        String renamed    = MetricsConstants.METRIC_NAME_RENAMES.getOrDefault(metricName, metricName);

        return renamed + rest;
    }

    private String renameTagValues(String line)
    {
        for (Map.Entry<String, String> entry : MetricsConstants.TAG_VALUE_RENAMES.entrySet())
        {
            line = line.replace(
                    "=\"" + entry.getKey() + "\"",
                    "=\"" + entry.getValue() + "\""
            );
        }
        return line;
    }

    private String addTypeAndTimestamp(String line, String timestamp)
    {
        int endIndex = line.contains("{") ? line.indexOf("{") : line.indexOf(" ");

        String renamedMetric  = line.substring(0, endIndex);

        // Extract original metric name from renamed map
        String originalMetric = MetricsConstants.METRIC_NAME_RENAMES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(renamedMetric))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(renamedMetric);

        // Extract type directly from original metric name
        String type = extractType(originalMetric);

        int lastClosingBrace = line.lastIndexOf("}");

        if (lastClosingBrace != -1)
        {
            return line.substring(0, lastClosingBrace) + ",type=\"" + type + "\"" + ",timestamp=\"" + timestamp + "\"}"
                    + line.substring(lastClosingBrace + 1);
        }
        else
        {
            int spaceIndex    = line.lastIndexOf(" ");
            String metricName = line.substring(0, spaceIndex);
            String value      = line.substring(spaceIndex);
            return metricName + "{type=\"" + type + "\"" + ",timestamp=\"" + timestamp + "\"}" + value;
        }
    }

    private String extractType(String metricName)
    {
        String[] parts = metricName.split("_");
        // jvm_memory or jvm_threads needs first two parts
        if (parts.length >= 2 && parts[0].equals("jvm"))
        {
            return parts[0] + "_" + parts[1];
        }
        return parts[0];
    }
}