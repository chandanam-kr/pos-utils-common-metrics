package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExceptionMapping
{

    // Which exception to map
    Class<? extends Throwable> exception();

    // Specific metric name for this exception
    String metricName();

    // Is this exception critical?
    boolean critical() default false;

    // Additional tags specific to this exception
    String[] tags() default {};
}