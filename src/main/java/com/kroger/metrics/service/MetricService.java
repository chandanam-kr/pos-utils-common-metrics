package com.kroger.metrics.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MetricService
{
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a MetricService with the provided MeterRegistry.
     * @param meterRegistry the MeterRegistry to use for metrics
     */
    public MetricService(@Lazy MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Increments a counter metric with the given name and standard tags.
     * @param metricName the name of the metric
     */
    public void count(String metricName)
    {
        meterRegistry.counter(metricName + "_total", baseTags()).increment();
    }

    /**
     * Increments a counter metric with the given name and custom tags.
     * @param metricName the name of the metric
     * @param tags key-value pairs for tags (even number of elements)
     */
    public void count(String metricName, String... tags)
    {
        meterRegistry.counter(metricName + "_total", buildTags(tags)).increment();
    }

    /**
     * Increments a counter metric if the condition is true.
     * @param condition the condition to check
     * @param metricName the name of the metric
     * @param tags key-value pairs for tags (even number of elements)
     */
    public void countIf(boolean condition, String metricName, String... tags)
    {
        if (condition) 
        {
            meterRegistry.counter(metricName + "_total", buildTags(tags)).increment();
        }
    }

    /**
     * Tracks an exception by incrementing a counter with exception details as tags.
     * @param metricName the name of the metric
     * @param throwable the exception to track
     * @param tags key-value pairs for tags (even number of elements)
     */
    public void trackException(String metricName, Throwable throwable, String... tags)
    {
        List<Tag> allTags = buildExceptionTags(throwable, false, tags);
        meterRegistry.counter(metricName + "_total", allTags).increment();
    }

    /**
     * Tracks a critical exception by incrementing a counter and logging the error.
     * @param metricName the name of the metric
     * @param throwable the critical exception to track
     * @param tags key-value pairs for tags (even number of elements)
     */
    public void trackCritical(String metricName, Throwable throwable, String... tags)
    {
        List<Tag> allTags = buildExceptionTags(throwable, true, tags);
        meterRegistry.counter(metricName + "_total", allTags).increment();
        log.error("CRITICAL [{}]: {}", metricName, throwable.getMessage());
    }

    /**
     * Tracks an error by incrementing a counter with an error message and status tag.
     * @param metricName the name of the metric
     * @param message the error message
     * @param tags key-value pairs for tags (even number of elements)
     */
    public void trackError(String metricName, String message, String... tags)
    {
        List<Tag> allTags = buildTags(tags);
        allTags.add(Tag.of("message", message != null ? message : "no_message"));
        allTags.add(Tag.of("status", "error"));
        meterRegistry.counter(metricName + "_total", allTags).increment();
    }

    /**
     * Records the duration of an event in milliseconds as a timer metric.
     * @param metricName the name of the metric
     * @param durationMs the duration in milliseconds
     * @param tags key-value pairs for tags (even number of elements)
     */
    public void recordTime(String metricName, long durationMs, String... tags)
    {
        Timer.builder(metricName + "_duration_seconds")
                .tags(buildTags(tags))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Builds a list of standard tags for metrics (class and method).
     * @return list of standard tags
     */
    private List<Tag> baseTags()
    {
        List<Tag> tags = new ArrayList<>();
        addStandardTags(tags);
        return tags;
    }

    /**
     * Builds a list of tags from the provided key-value pairs and adds standard tags.
     * @param tags key-value pairs for tags (even number of elements)
     * @return list of tags
     */
    private List<Tag> buildTags(String... tags)
    {
        List<Tag> tagList = new ArrayList<>();
        if (tags != null && tags.length > 0)
        {
            for (int i = 0; i < tags.length - 1; i += 2)
                tagList.add(Tag.of(tags[i], tags[i + 1]));
        }
        addStandardTags(tagList);
        return tagList;
    }

    /**
     * Builds a list of tags for exceptions, including exception details and critical flag.
     * @param throwable the exception
     * @param critical whether the exception is critical
     * @param tags key-value pairs for tags (even number of elements)
     * @return list of tags
     */
    private List<Tag> buildExceptionTags(Throwable throwable, boolean critical, String... tags)
    {
        List<Tag> tagList = new ArrayList<>();

        if (tags != null && tags.length > 0)
        {
            for (int i = 0; i < tags.length - 1; i += 2)
                tagList.add(Tag.of(tags[i], tags[i + 1]));
        }

        tagList.add(Tag.of("exception", throwable.getClass().getSimpleName()));
        tagList.add(Tag.of("message",   throwable.getMessage() != null ? throwable.getMessage() : "no_message"));
        tagList.add(Tag.of("status",   "failure"));
        tagList.add(Tag.of("critical", String.valueOf(critical)));

        addStandardTags(tagList);
        return tagList;
    }

    /**
     * Adds standard tags (class and method) to the provided tag list.
     * @param tags the tag list to add to
     */
    private void addStandardTags(List<Tag> tags)
    {
        StackTraceElement caller = findCaller();
        if (caller != null)
        {
            String fullClassName = caller.getClassName();
            String className     = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

            tags.add(Tag.of("class",  className));
            tags.add(Tag.of("method", caller.getMethodName()));
        }
    }

    /**
     * Finds the caller's stack trace element outside of standard Java and this class.
     * @return the caller's StackTraceElement, or null if not found
     */
    private StackTraceElement findCaller()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stackTrace)
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