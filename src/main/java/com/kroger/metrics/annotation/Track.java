package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Track
{
    Class<? extends Throwable> type();

    String metric();

    boolean critical() default false;
}