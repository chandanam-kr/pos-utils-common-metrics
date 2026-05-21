/*
package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.CaptureMetric;
import com.kroger.metrics.annotation.MetricType;
import io.micrometer.core.instrument.Counter;
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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class CaptureMetricAspect {

    private final MeterRegistry meterRegistry;

    public CaptureMetricAspect(@Lazy MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(captureMetric)")
    public Object capture(ProceedingJoinPoint joinPoint, CaptureMetric captureMetric) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method             = signature.getMethod();
        String className          = joinPoint.getTarget().getClass().getSimpleName();
        String methodName         = method.getName();
        String eventName          = captureMetric.eventName();
        Object[] args             = joinPoint.getArgs();
        Parameter[] parameters    = method.getParameters();

        // Build tags
        List<Tag> tags = buildTags(
                captureMetric,
                className,
                methodName,
                parameters,
                args
        );

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            // Capture result as tag if enabled
            if (captureMetric.captureResult() && result != null) {
                tags.add(Tag.of("result", result.toString()));
            }

            // Record business metrics on success
            recordBusinessMetrics(captureMetric.type(), eventName, tags, startTime, "success");

            return result;

        } catch (Throwable throwable) {

            // Handle exception tracking
            if (captureMetric.captureExceptions()) {
                handleException(
                        throwable,
                        captureMetric,
                        eventName,
                        tags,
                        startTime,
                        className,
                        methodName
                );
            }

            throw throwable;
        }
    }

    // ── Build Tags ─────────────────────────────────────────────────
    private List<Tag> buildTags(CaptureMetric captureMetric,
                                String className,
                                String methodName,
                                Parameter[] parameters,
                                Object[] args) {
        List<Tag> tags = new ArrayList<>();

        // Default tags
        tags.add(Tag.of("class",  className));
        tags.add(Tag.of("method", methodName));

        // Resolve annotation tags
        // Supports #paramName to reference method args
        // Example: "userId=#userId" resolves to actual userId value
        for (String tag : captureMetric.tags()) {
            String[] keyValue = tag.split("=", 2);
            if (keyValue.length == 2) {
                String key   = keyValue[0].trim();
                String value = keyValue[1].trim();

                // Resolve #paramName to actual value
                if (value.startsWith("#")) {
                    String paramName    = value.substring(1);
                    String resolvedValue = resolveParam(paramName, parameters, args);
                    tags.add(Tag.of(key, resolvedValue));
                } else {
                    tags.add(Tag.of(key, value));
                }
            }
        }

        // Capture all args as tags if enabled
        if (captureMetric.captureArgs()) {
            for (int i = 0; i < parameters.length; i++) {
                String paramName  = parameters[i].getName();
                String paramValue = args[i] != null ? args[i].toString() : "null";
                tags.add(Tag.of("arg_" + paramName, paramValue));
            }
        }

        return tags;
    }

    // ── Resolve #paramName to Actual Value ─────────────────────────
    private String resolveParam(String paramName,
                                Parameter[] parameters,
                                Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                return args[i] != null ? args[i].toString() : "null";
            }
        }
        return "unresolved";
    }

    // ── Record Business Metrics ────────────────────────────────────
    private void recordBusinessMetrics(MetricType type,
                                       String eventName,
                                       List<Tag> tags,
                                       long startTime,
                                       String status) {
        List<Tag> statusTags = new ArrayList<>(tags);
        statusTags.add(Tag.of("status", status));

        switch (type) {

            case COUNTER -> {
                // Just count occurrences
                meterRegistry.counter(eventName + "_total", statusTags)
                        .increment();
            }

            case TIMER -> {
                // Just measure duration
                Timer.builder(eventName + "_duration")
                        .tags(statusTags)
                        .register(meterRegistry)
                        .record(System.currentTimeMillis() - startTime,
                                TimeUnit.MILLISECONDS);
            }

            case GAUGE -> {
                // Track current value
                meterRegistry.gauge(eventName + "_current",
                        statusTags,
                        System.currentTimeMillis() - startTime);
            }

            case ALL -> {
                // Counter + Timer together
                meterRegistry.counter(eventName + "_total", statusTags)
                        .increment();

                Timer.builder(eventName + "_duration")
                        .tags(statusTags)
                        .register(meterRegistry)
                        .record(System.currentTimeMillis() - startTime,
                                TimeUnit.MILLISECONDS);
            }
        }
    }

    // ── Handle Exception ───────────────────────────────────────────
    private void handleException(Throwable throwable,
                                 CaptureMetric captureMetric,
                                 String eventName,
                                 List<Tag> tags,
                                 long startTime,
                                 String className,
                                 String methodName) {

        // Check if exception should be ignored
        boolean shouldIgnore = Arrays.stream(captureMetric.ignoreExceptions())
                .anyMatch(ex -> ex.isInstance(throwable));

        if (shouldIgnore) {
            log.debug("Ignoring exception [{}] for metric [{}]",
                    throwable.getClass().getSimpleName(), eventName);
            return;
        }

        // Build exception tags
        List<Tag> exceptionTags = new ArrayList<>(tags);
        exceptionTags.add(Tag.of("exception", throwable.getClass().getSimpleName()));
        exceptionTags.add(Tag.of("status",    "failure"));
        exceptionTags.add(Tag.of("message",   throwable.getMessage() != null
                ? throwable.getMessage()
                : "no_message"));

        // Record failure metric
        recordBusinessMetrics(captureMetric.type(), eventName, exceptionTags, startTime, "failure");

        // Check if exception is critical
        boolean isCritical = Arrays.stream(captureMetric.criticalExceptions())
                .anyMatch(ex -> ex.isInstance(throwable));

        if (isCritical) {
            // Increment critical counter separately
            meterRegistry.counter(
                    eventName + "_critical_failure",
                    exceptionTags
            ).increment();

            log.error("CRITICAL exception tracked [{}] class [{}] method [{}]: {}",
                    eventName, className, methodName, throwable.getMessage());
        } else {
            log.warn("Exception tracked [{}] class [{}] method [{}]: {}",
                    eventName, className, methodName, throwable.getMessage());
        }
    }
}*/
