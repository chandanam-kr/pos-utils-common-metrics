package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.Metric;
import com.kroger.metrics.annotation.MetricType;
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
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
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
        String className       = joinPoint.getTarget().getClass().getSimpleName();
        String methodName      = joinPoint.getSignature().getName();
        Object[] args          = joinPoint.getArgs();
        Parameter[] parameters = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getParameters();

        log.info(">>> Metric triggered for [{}]", metric.on());

        List<Tag> baseTags = buildTags(metric.tags(), className, methodName, parameters, args);
        long startTime     = System.currentTimeMillis();

        try
        {
            Object result     = joinPoint.proceed();
            List<Tag> success = new ArrayList<>(baseTags);
            success.add(Tag.of("status", "success"));

            record(metric.type(), metric.on(), success, startTime);

            return result;
        }
        catch (Throwable throwable)
        {
            throw throwable;
        }
    }

    private void record(MetricType type, String eventName, List<Tag> tags, long startTime)
    {
        switch (type)
        {
            case COUNTER -> counter(eventName + "_total", tags);

            case TIMER   -> timer(eventName + "_duration_seconds", tags, startTime);

            case GAUGE   -> gauge(eventName, tags, startTime);

            case ALL     ->
            {
                counter(eventName + "_total", tags);
                timer(eventName + "_duration_seconds", tags, startTime);
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

    public List<Tag> buildTags(String[] annotationTags, String className,
                               String methodName, Parameter[] parameters, Object[] args)
    {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("class",  className));
        tags.add(Tag.of("method", methodName));

        for (String tag : annotationTags)
        {
            String[] kv = tag.split("=", 2);
            if (kv.length != 2) continue;

            String key   = kv[0].trim();
            String value = kv[1].trim();

            tags.add(Tag.of(key, value.startsWith("#")
                    ? resolve(value.substring(1), parameters, args)
                    : value));
        }

        return tags;
    }

    public String resolve(String expression, Parameter[] parameters, Object[] args)
    {
        return expression.contains(".")
                ? resolveNested(expression, parameters, args)
                : resolveSimple(expression, parameters, args);
    }

    private String resolveSimple(String paramName, Parameter[] parameters, Object[] args)
    {
        for (int i = 0; i < parameters.length; i++)
        {
            if (parameters[i].getName().equals(paramName))
                return args[i] != null ? args[i].toString() : "null";
        }
        return "unresolved";
    }

    private String resolveNested(String expression, Parameter[] parameters, Object[] args)
    {
        try
        {
            String[] parts   = expression.split("\\.", 2);
            String paramName = parts[0];
            String fieldName = parts[1];

            for (int i = 0; i < parameters.length; i++)
            {
                if (!parameters[i].getName().equals(paramName) || args[i] == null)
                    continue;

                try
                {
                    String getter = "get" + Character.toUpperCase(fieldName.charAt(0))
                            + fieldName.substring(1);
                    Object value  = args[i].getClass().getMethod(getter).invoke(args[i]);
                    return value != null ? value.toString() : "null";
                }
                catch (NoSuchMethodException e)
                {
                    Field field = args[i].getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(args[i]);
                    return value != null ? value.toString() : "null";
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Could not resolve [{}]: {}", expression, e.getMessage());
        }
        return "unresolved";
    }
}