package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.CustomMetric;
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
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class CustomMetricAspect
{

    private final MeterRegistry meterRegistry;

    public CustomMetricAspect(@Lazy MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(customMetric)")
    public Object capture(ProceedingJoinPoint joinPoint, CustomMetric customMetric) throws Throwable
    {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method             = signature.getMethod();
        String className          = joinPoint.getTarget().getClass().getSimpleName();
        String methodName         = method.getName();
        Object[] args             = joinPoint.getArgs();
        Parameter[] parameters    = method.getParameters();

        log.info(">>> CustomMetric triggered for eventName [{}]", customMetric.eventName());

        List<Tag> baseTags = buildBaseTags(
                customMetric.tags(),
                customMetric.captureArgs(),
                className,
                methodName,
                parameters,
                args
        );

        long startTime = System.currentTimeMillis();

        try
        {
            Object result = joinPoint.proceed();

            List<Tag> successTags = new ArrayList<>(baseTags);
            successTags.add(Tag.of("status", "success"));

            if (customMetric.captureResult() && result != null)
            {
                successTags.add(Tag.of("result", result.toString()));
            }

            recordMetrics(customMetric.type(), customMetric.eventName(), successTags, startTime);

            return result;

        }
        catch (Throwable throwable)
        {
            throw throwable;
        }
    }

    private void recordMetrics(MetricType type, String eventName, List<Tag> tags, long startTime)
    {
        switch (type)
        {
            case COUNTER -> meterRegistry
                    .counter(eventName + "_total", tags)
                    .increment();

            case TIMER -> Timer.builder(eventName + "_duration")
                    .tags(tags)
                    .register(meterRegistry)
                    .record(System.currentTimeMillis() - startTime,
                            TimeUnit.MILLISECONDS);

            case GAUGE -> meterRegistry.gauge(
                    eventName + "_current",
                    tags,
                    System.currentTimeMillis() - startTime);

            case ALL ->
            {
                meterRegistry.counter(eventName + "_total", tags).increment();

                Timer.builder(eventName + "_duration")
                        .tags(tags)
                        .register(meterRegistry)
                        .record(System.currentTimeMillis() - startTime,
                                TimeUnit.MILLISECONDS);
            }
        }
    }

    public List<Tag> buildBaseTags(String[] annotationTags, boolean captureArgs, String className,
                                   String methodName, Parameter[] parameters, Object[] args)
    {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("class",  className));
        tags.add(Tag.of("method", methodName));

        for (String tag : annotationTags)
        {
            String[] keyValue = tag.split("=", 2);
            if (keyValue.length == 2)
            {
                String key   = keyValue[0].trim();
                String value = keyValue[1].trim();

                if (value.startsWith("#"))
                {
                    String resolved = value.contains(".")
                            ? resolveNestedParam(value.substring(1), parameters, args)
                            : resolveParam(value.substring(1), parameters, args);
                    tags.add(Tag.of(key, resolved));
                }
                else
                {
                    tags.add(Tag.of(key, value));
                }
            }
        }

        if (captureArgs)
        {
            for (int i = 0; i < parameters.length; i++)
            {
                if (args[i] != null && !(args[i] instanceof org.springframework.validation.BindingResult))
                {
                    tags.add(Tag.of("arg_" + parameters[i].getName(), args[i].toString()));
                }
            }
        }

        return tags;
    }

    public String resolveParam(String paramName, Parameter[] parameters, Object[] args)
    {
        for (int i = 0; i < parameters.length; i++)
        {
            if (parameters[i].getName().equals(paramName))
            {
                return args[i] != null ? args[i].toString() : "null";
            }
        }
        return "unresolved";
    }

    public String resolveNestedParam(String expression, Parameter[] parameters, Object[] args) {
        try
        {
            String[] parts   = expression.split("\\.", 2);
            String paramName = parts[0];
            String fieldName = parts[1];

            for (int i = 0; i < parameters.length; i++)
            {
                if (parameters[i].getName().equals(paramName) && args[i] != null)
                {
                    String getterName = "get" +
                            Character.toUpperCase(fieldName.charAt(0)) +
                            fieldName.substring(1);
                    try
                    {
                        Method getter = args[i].getClass().getMethod(getterName);
                        Object value  = getter.invoke(args[i]);
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
        } catch (Exception e)
        {
            log.warn("Could not resolve nested param [{}]: {}", expression, e.getMessage());
        }
        return "unresolved";
    }
}