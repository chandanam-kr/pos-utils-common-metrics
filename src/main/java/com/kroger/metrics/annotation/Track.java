package com.kroger.metrics.annotation;

import java.lang.annotation.*;

/**
 * Maps a specific exception type to a custom metric name.
 * Used inside @OnException.track() array.
 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Track
{
    Class<? extends Throwable> type();

    String metric();

    boolean critical() default false;
}