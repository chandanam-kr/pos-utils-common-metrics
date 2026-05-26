package com.kroger.metrics.service;

import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for programmatic metric tracking.
 * Use this when you need conditional tracking or business logic based metrics.
 * All operations are safe and will not break user code if metric recording fails.
 */
@Slf4j
@Service
public class MetricService
{
    private final MeterRegistry meterRegistry;

    public MetricService(@Lazy MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Increments a counter metric with standard tags only.
     */
    public void count(String metricName)
    {
        safelyRecord(() -> meterRegistry.counter(metricName + MetricsConstants.TOTAL_SUFFIX, baseTags()).increment(), metricName);
    }

    /**
     * Increments a counter metric with custom tags.
     */
    public void count(String metricName, String... tags)
    {
        safelyRecord(() -> meterRegistry.counter(metricName + MetricsConstants.TOTAL_SUFFIX, buildTags(tags)).increment(), metricName);
    }

    /**
     * Increments a counter only if condition is true.
     */
    public void countIf(boolean condition, String metricName, String... tags)
    {
        if (condition) count(metricName, tags);
    }

    /**
     * Tracks an exception with exception details as tags.
     */
    public void trackException(String metricName, Throwable throwable, String... tags)
    {
        safelyRecord(() -> meterRegistry.counter(metricName + MetricsConstants.TOTAL_SUFFIX, buildExceptionTags(throwable, false, tags)).increment(), metricName);
    }

    /**
     * Tracks a critical exception and logs as ERROR.
     */
    public void trackCritical(String metricName, Throwable throwable, String... tags)
    {
        safelyRecord(() ->
        {
            meterRegistry.counter(metricName + MetricsConstants.TOTAL_SUFFIX, buildExceptionTags(throwable, true, tags)).increment();
            log.error(MetricsConstants.LOG_CRITICAL_EXCEPTION, metricName, throwable.getMessage());
        }, metricName);
    }

    /**
     * Tracks an error log with custom message.
     */
    public void trackError(String metricName, String message, String... tags)
    {
        safelyRecord(() ->
        {
            List<Tag> allTags = buildTags(tags);
            allTags.add(Tag.of(MetricsConstants.TAG_MESSAGE, message != null ? message : MetricsConstants.NO_MESSAGE));
            allTags.add(Tag.of(MetricsConstants.TAG_STATUS, MetricsConstants.STATUS_ERROR));
            meterRegistry.counter(metricName + MetricsConstants.TOTAL_SUFFIX, allTags).increment();
        }, metricName);
    }

    /**
     * Records duration of an operation in milliseconds.
     */
    public void recordTime(String metricName, long durationMs, String... tags)
    {
        safelyRecord(() ->
                        Timer.builder(metricName + MetricsConstants.SUFFIX_TIMER)
                                .tags(buildTags(tags))
                                .register(meterRegistry)
                                .record(durationMs, TimeUnit.MILLISECONDS),
                metricName);
    }

    /**
     * Safely executes metric recording. Catches all exceptions to prevent
     * library failures from breaking user code.
     */
    private void safelyRecord(Runnable action, String metricName)
    {
        try
        {
            action.run();
        }
        catch (Exception e)
        {
            log.warn(MetricsConstants.LOG_FAILED_RECORD_METRIC, metricName, e.getMessage());
        }
    }

    /**
     * Builds base tags with class and method from stack trace.
     */
    private List<Tag> baseTags()
    {
        List<Tag> tags = new ArrayList<>();
        addStandardTags(tags);
        return tags;
    }

    /**
     * Builds tags from key-value pairs and adds standard tags.
     */
    private List<Tag> buildTags(String... tags)
    {
        List<Tag> tagList = new ArrayList<>();
        addKeyValuePairs(tagList, tags);
        addStandardTags(tagList);
        return tagList;
    }

    /**
     * Builds exception tags with exception details.
     */
    private List<Tag> buildExceptionTags(Throwable throwable, boolean critical, String... tags)
    {
        List<Tag> tagList = new ArrayList<>();
        addKeyValuePairs(tagList, tags);

        tagList.add(Tag.of(MetricsConstants.TAG_EXCEPTION, throwable.getClass().getSimpleName()));
        tagList.add(Tag.of(MetricsConstants.TAG_MESSAGE,   throwable.getMessage() != null ? throwable.getMessage() : MetricsConstants.NO_MESSAGE));
        tagList.add(Tag.of(MetricsConstants.TAG_STATUS,   MetricsConstants.STATUS_FAILURE));
        tagList.add(Tag.of(MetricsConstants.TAG_CRITICAL, String.valueOf(critical)));

        addStandardTags(tagList);
        return tagList;
    }

    /**
     * Adds key-value pairs from String varargs to tag list.
     */
    private void addKeyValuePairs(List<Tag> tagList, String... tags)
    {
        if (tags == null || tags.length == 0)
            return;

        for (int i = 0; i < tags.length - 1; i += 2)
        {
            tagList.add(Tag.of(tags[i], tags[i + 1]));
        }
    }

    /**
     * Adds class and method tags by inspecting the stack trace.
     */
    private void addStandardTags(List<Tag> tags)
    {
        StackTraceElement caller = findCaller();
        if (caller == null)
            return;

        String fullClassName = caller.getClassName();
        String className     = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

        tags.add(Tag.of(MetricsConstants.TAG_CLASS,  className));
        tags.add(Tag.of(MetricsConstants.TAG_METHOD, caller.getMethodName()));
    }

    /**
     * Finds the actual caller class by skipping framework and library frames.
     */
    private StackTraceElement findCaller()
    {
        for (StackTraceElement element : Thread.currentThread().getStackTrace())
        {
            String className = element.getClassName();

            if (className.equals("java.lang.Thread")) continue;
            if (className.equals(this.getClass().getName())) continue;
            if (className.startsWith("java.")) continue;
            if (className.startsWith("sun.")) continue;
            if (className.startsWith("jdk.")) continue;

            return element;
        }
        return null;
    }
}