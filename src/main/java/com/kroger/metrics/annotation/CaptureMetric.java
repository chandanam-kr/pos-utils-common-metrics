package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CaptureMetric {
    String eventName();
    MetricType type() default MetricType.COUNTER;
    String[] tags() default {};
    boolean captureArgs() default false;
    boolean captureResult() default false;
    boolean captureExceptions() default false;

    Class<? extends Throwable>[] ignoreExceptions() default {};
    ExceptionMetric[] exceptionMetrics() default {};
    String defaultExceptionMetricName() default "";
}