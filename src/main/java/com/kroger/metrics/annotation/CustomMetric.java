package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomMetric
{

    // REQUIRED - Event name for metric
    String eventName();

    // Type of metric to capture
    MetricType type() default MetricType.COUNTER;

    // Custom tags key value pairs
    String[] tags() default {};

    // Capture method arguments as tags
    boolean captureArgs() default false;

    // Capture return value as tag
    boolean captureResult() default false;
}
