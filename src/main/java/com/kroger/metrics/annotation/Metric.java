package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Metric
{
    String on();
    MetricType type() default MetricType.ALL;
    String[] tags() default {};
}