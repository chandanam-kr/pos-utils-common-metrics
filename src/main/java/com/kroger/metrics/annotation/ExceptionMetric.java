package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExceptionMetric
{

    // REQUIRED - Base event name
    String eventName();

    // Map each exception to its own metric name and config
    ExceptionMapping[] exceptionMappings() default {};

    // Exceptions to ignore completely
    Class<? extends Throwable>[] ignoreExceptions() default {};

    // Default metric name for exceptions not in exceptionMappings
    String defaultMetricName() default "";

    // Tags common to all exceptions
    String[] tags() default {};
}