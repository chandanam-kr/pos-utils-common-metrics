package com.kroger.metrics.service;

import lombok.extern.slf4j.Slf4j;

/**
 * No-op implementation of MetricService.
 * Used when library is disabled via metrics.enabled=false
 * All methods do nothing to prevent NullPointerException in user code.
 */
@Slf4j
public class NoOpMetricService extends MetricService
{
    public NoOpMetricService()
    {
        super(null);
        log.warn("MetricService is DISABLED - using no-op implementation");
    }

    @Override
    public void count(String metricName)
    {
        // No operation
    }

    @Override
    public void count(String metricName, String... tags)
    {
        // No operation
    }

    @Override
    public void countIf(boolean condition, String metricName, String... tags)
    {
        // No operation
    }

    @Override
    public void trackException(String metricName, Throwable throwable, String... tags)
    {
        // No operation
    }

    @Override
    public void trackCritical(String metricName, Throwable throwable, String... tags)
    {
        // No operation
    }

    @Override
    public void trackError(String metricName, String message, String... tags)
    {
        // No operation
    }

    @Override
    public void recordTime(String metricName, long durationMs, String... tags)
    {
        // No operation
    }
}