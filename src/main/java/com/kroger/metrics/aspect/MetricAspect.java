package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.Metric;
import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.kroger.metrics.constants.MetricsConstants.GET;
import static com.kroger.metrics.constants.MetricsConstants.METRIC_RECORDING_FAILED_LOG;

/**
 * Records success metrics for methods annotated with @Metric.
 * Failures are caught silently to never break user code.
 */
@Slf4j
@Aspect
@Component
public class MetricAspect
{
    private final MeterRegistry meterRegistry;

    public MetricAspect(@Lazy MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(metric)")
    public Object capture(ProceedingJoinPoint joinPoint, Metric metric) throws Throwable
    {
        long startTime = System.currentTimeMillis();

        recordSafely(joinPoint, metric, startTime);
        return joinPoint.proceed();
    }

    private void recordSafely(ProceedingJoinPoint joinPoint, Metric metric, long startTime)
    {
        try
        {
            List<Tag> tags = buildTags(joinPoint, metric);
            tags.add(Tag.of(MetricsConstants.TAG_STATUS, MetricsConstants.STATUS_SUCCESS));

            record(metric, tags, startTime);
        }
        catch (Exception e)
        {
            log.warn(METRIC_RECORDING_FAILED_LOG, metric.on(), e.getMessage());
        }
    }

    private void record(Metric metric, List<Tag> tags, long startTime)
    {
        switch (metric.type())
        {
            case COUNTER -> counter(metric.on() + MetricsConstants.SUFFIX_COUNTER, tags);
            case TIMER   -> timer(metric.on() + MetricsConstants.SUFFIX_TIMER, tags, startTime);
            case GAUGE   -> gauge(metric.on(), tags, startTime);
            case ALL     ->
            {
                counter(metric.on() + MetricsConstants.SUFFIX_COUNTER, tags);
                timer(metric.on() + MetricsConstants.SUFFIX_TIMER, tags, startTime);
            }
        }
    }

    private void counter(String name, List<Tag> tags)
    {
        meterRegistry.counter(name, tags).increment();
    }

    private void timer(String name, List<Tag> tags, long startTime)
    {
        Timer.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
    }

    private void gauge(String name, List<Tag> tags, long startTime)
    {
        meterRegistry.gauge(name, tags, System.currentTimeMillis() - startTime);
    }

    /**
     * Builds tags from method context and annotation.
     * Shared with OnExceptionAspect via public method.
     */
    public List<Tag> buildTags(ProceedingJoinPoint joinPoint, Metric metric)
    {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName         = signature.getName();
        Parameter[] parameters    = signature.getMethod().getParameters();

        return buildTags(metric.tags(), joinPoint.getTarget().getClass().getSimpleName(),
                methodName, parameters, joinPoint.getArgs());
    }

    public List<Tag> buildTags(String[] annotationTags, String className,
                               String methodName, Parameter[] parameters, Object[] args)
    {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(MetricsConstants.TAG_CLASS, className));
        tags.add(Tag.of(MetricsConstants.TAG_METHOD, methodName));

        for (String tag : annotationTags)
            parseTag(tag, parameters, args).ifPresent(tags::add);

        return tags;
    }

    private java.util.Optional<Tag> parseTag(String tag, Parameter[] parameters, Object[] args)
    {
        String[] kv = tag.split(MetricsConstants.TAG_SEPARATOR, 2);
        if (kv.length != 2)
            return Optional.empty();

        String key   = kv[0].trim();
        String value = kv[1].trim();

        String resolved = value.startsWith(MetricsConstants.TAG_DYNAMIC_PREFIX)
                ? resolve(value.substring(1), parameters, args)
                : value;

        return Optional.of(Tag.of(key, resolved));
    }

    private String resolve(String expression, Parameter[] parameters, Object[] args)
    {
        return expression.contains(MetricsConstants.TAG_NESTED_SEPARATOR)
                ? resolveNested(expression, parameters, args)
                : resolveSimple(expression, parameters, args);
    }

    private String resolveSimple(String paramName, Parameter[] parameters, Object[] args)
    {
        for (int i = 0; i < parameters.length; i++)
        {
            if (parameters[i].getName().equals(paramName))
                return args[i] != null ? args[i].toString() : MetricsConstants.NULL_VALUE;
        }
        return MetricsConstants.UNRESOLVED;
    }

    private String resolveNested(String expression, Parameter[] parameters, Object[] args)
    {
        String[] parts   = expression.split("\\.", 2);
        String paramName = parts[0];
        String fieldName = parts[1];

        for (int i = 0; i < parameters.length; i++)
        {
            if (!parameters[i].getName().equals(paramName) || args[i] == null)
                continue;

            return extractField(args[i], fieldName);
        }
        return MetricsConstants.UNRESOLVED;
    }

    private String extractField(Object obj, String fieldName)
    {
        try
        {
            Object value = tryGetter(obj, fieldName);
            return value != null ? value.toString() : MetricsConstants.NULL_VALUE;
        }
        catch (Exception e)
        {
            return MetricsConstants.UNRESOLVED;
        }
    }

    private Object tryGetter(Object obj, String fieldName) throws Exception
    {
        String getter = GET + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        try
        {
            return obj.getClass().getMethod(getter).invoke(obj);
        }
        catch (NoSuchMethodException e)
        {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        }
    }
}