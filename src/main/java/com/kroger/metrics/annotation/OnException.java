package com.kroger.metrics.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnException
{
    String on() default "";

    String[] tags() default {};

    Class<? extends Throwable>[] ignore() default {};

    Track[] track() default {};
}