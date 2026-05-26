package com.kroger.metrics.annotation;

import java.lang.annotation.*;

/**
 * Marks a method for exception tracking.
 * Behavior:
 * - If track list defined: only tracks listed exceptions
 * - If no track list and "on" defined: tracks all as on_failure
 * - Exceptions in ignore list are always skipped
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnException
{
    String on() default "";

    String[] tags() default {};

    Class<? extends Throwable>[] ignore() default {};

    Track[] track() default {};
}