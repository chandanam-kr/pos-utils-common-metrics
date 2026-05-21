package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.ExceptionMapping;
import com.kroger.metrics.annotation.ExceptionMetric;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
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
import java.util.Optional;

@Aspect
@Component
@Slf4j
public class ExceptionMetricAspect {

    private final MeterRegistry      meterRegistry;
    private final CustomMetricAspect customMetricAspect;

    public ExceptionMetricAspect(@Lazy MeterRegistry meterRegistry,
                                 CustomMetricAspect customMetricAspect) {
        this.meterRegistry       = meterRegistry;
        this.customMetricAspect  = customMetricAspect;
    }

    @Around("@annotation(exceptionMetric)")
    public Object capture(ProceedingJoinPoint joinPoint,
                          ExceptionMetric exceptionMetric) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method             = signature.getMethod();
        String className          = joinPoint.getTarget().getClass().getSimpleName();
        String methodName         = method.getName();
        Object[] args             = joinPoint.getArgs();
        Parameter[] parameters    = method.getParameters();

        log.info(">>> ExceptionMetric triggered for eventName [{}]",
                exceptionMetric.eventName());

        List<Tag> baseTags = customMetricAspect.buildBaseTags(
                exceptionMetric.tags(),
                false,
                className,
                methodName,
                parameters,
                args
        );

        try {
            return joinPoint.proceed();

        } catch (Throwable throwable) {
            handleException(
                    throwable,
                    exceptionMetric,
                    baseTags,
                    className,
                    methodName
            );
            throw throwable;
        }
    }

    private void handleException(Throwable throwable,
                                 ExceptionMetric exceptionMetric,
                                 List<Tag> baseTags,
                                 String className,
                                 String methodName) {

        boolean shouldIgnore = Arrays.stream(exceptionMetric.ignoreExceptions())
                .anyMatch(ex -> ex.isInstance(throwable));

        if (shouldIgnore) {
            log.debug("Ignoring exception [{}] for metric [{}]",
                    throwable.getClass().getSimpleName(),
                    exceptionMetric.eventName());
            return;
        }

        Optional<ExceptionMapping> matchedMapping = Arrays
                .stream(exceptionMetric.exceptionMappings())
                .filter(mapping -> mapping.exception().isInstance(throwable))
                .findFirst();

        if (matchedMapping.isPresent()) {
            recordMappedException(
                    matchedMapping.get(),
                    baseTags,
                    throwable,
                    className,
                    methodName
            );
        } else {
            recordDefaultException(
                    exceptionMetric,
                    baseTags,
                    throwable,
                    className,
                    methodName
            );
        }
    }

    private void recordMappedException(ExceptionMapping mapping,
                                       List<Tag> baseTags,
                                       Throwable throwable,
                                       String className,
                                       String methodName) {

        List<Tag> exceptionTags = new ArrayList<>(baseTags);
        exceptionTags.add(Tag.of("status",    "failure"));
        exceptionTags.add(Tag.of("exception", throwable.getClass().getSimpleName()));
        exceptionTags.add(Tag.of("message",   throwable.getMessage() != null
                ? throwable.getMessage() : "no_message"));
        exceptionTags.add(Tag.of("critical",  String.valueOf(mapping.critical())));

        String[] customTags = mapping.tags();
        if (customTags.length % 2 == 0) {
            for (int i = 0; i < customTags.length; i += 2) {
                exceptionTags.add(Tag.of(customTags[i], customTags[i + 1]));
            }
        }

        meterRegistry.counter(
                mapping.metricName() + "_total",
                exceptionTags
        ).increment();

        if (mapping.critical()) {
            log.error("CRITICAL exception metric [{}] class [{}] method [{}]: {}",
                    mapping.metricName(), className, methodName,
                    throwable.getMessage());
        } else {
            log.warn("Exception metric [{}] class [{}] method [{}]: {}",
                    mapping.metricName(), className, methodName,
                    throwable.getMessage());
        }
    }

    private void recordDefaultException(ExceptionMetric exceptionMetric,
                                        List<Tag> baseTags,
                                        Throwable throwable,
                                        String className,
                                        String methodName) {

        String metricName = exceptionMetric.defaultMetricName().isEmpty()
                ? exceptionMetric.eventName() + "_failure"
                : exceptionMetric.defaultMetricName();

        List<Tag> defaultTags = new ArrayList<>(baseTags);
        defaultTags.add(Tag.of("status",    "failure"));
        defaultTags.add(Tag.of("exception", throwable.getClass().getSimpleName()));
        defaultTags.add(Tag.of("message",   throwable.getMessage() != null
                ? throwable.getMessage() : "no_message"));
        defaultTags.add(Tag.of("critical",  "false"));

        meterRegistry.counter(metricName + "_total", defaultTags).increment();

        log.warn("Default exception metric [{}] class [{}] method [{}]: {}",
                metricName, className, methodName, throwable.getMessage());
    }
}