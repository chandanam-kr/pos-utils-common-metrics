package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.OnException;
import com.kroger.metrics.annotation.Track;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Aspect
@Component
@Slf4j
public class OnExceptionAspect
{
    private final MeterRegistry meterRegistry;
    private final MetricAspect  metricAspect;

    public OnExceptionAspect(@Lazy MeterRegistry meterRegistry,
                             MetricAspect metricAspect)
    {
        this.meterRegistry = meterRegistry;
        this.metricAspect  = metricAspect;
    }

    @Around("@annotation(onException)")
    public Object capture(ProceedingJoinPoint joinPoint,
                          OnException onException) throws Throwable
    {
        String className       = joinPoint.getTarget().getClass().getSimpleName();
        String methodName      = joinPoint.getSignature().getName();
        Object[] args          = joinPoint.getArgs();
        Parameter[] parameters = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getParameters();

        List<Tag> baseTags = metricAspect.buildTags(
                onException.tags(), className, methodName, parameters, args);

        try
        {
            return joinPoint.proceed();
        }
        catch (Throwable throwable)
        {
            handleException(throwable, onException, baseTags, className, methodName);
            throw throwable;
        }
    }

    private void handleException(Throwable throwable, OnException onException,
                                 List<Tag> baseTags, String className, String methodName)
    {
        boolean ignored = Arrays.stream(onException.ignore())
                .anyMatch(ex -> ex.isInstance(throwable));

        if (ignored)
        {
            log.debug("Ignoring [{}] for class [{}] method [{}]",
                    throwable.getClass().getSimpleName(), className, methodName);
            return;
        }

        if (onException.track().length > 0)
            handleTrackedException(throwable, onException, baseTags, className, methodName);
        else
            handleDefaultException(throwable, onException, baseTags, className, methodName);
    }

    private void handleTrackedException(Throwable throwable, OnException onException,
                                        List<Tag> baseTags, String className, String methodName)
    {
        Optional<Track> matched = Arrays.stream(onException.track())
                .filter(t -> t.type().isInstance(throwable))
                .findFirst();

        if (matched.isEmpty())
        {
            log.debug("Exception [{}] not in track list for class [{}] method [{}]",
                    throwable.getClass().getSimpleName(), className, methodName);
            return;
        }

        Track track    = matched.get();
        List<Tag> tags = buildFailureTags(baseTags, throwable, track.critical());

        meterRegistry.counter(track.metric() + "_total", tags).increment();

        if (track.critical())
            log.error("CRITICAL [{}] class [{}] method [{}]: {}",
                    track.metric(), className, methodName, throwable.getMessage());
        else
            log.warn("Exception [{}] class [{}] method [{}]: {}",
                    track.metric(), className, methodName, throwable.getMessage());
    }

    private void handleDefaultException(Throwable throwable, OnException onException,
                                        List<Tag> baseTags, String className, String methodName)
    {
        if (onException.on().isEmpty())
        {
            log.warn("No 'on' defined for default tracking class [{}] method [{}]",
                    className, methodName);
            return;
        }

        String metricName = onException.on() + "_failure";
        List<Tag> tags    = buildFailureTags(baseTags, throwable, false);

        meterRegistry.counter(metricName + "_total", tags).increment();

        log.warn("Default exception [{}] class [{}] method [{}]: {}",
                metricName, className, methodName, throwable.getMessage());
    }

    private List<Tag> buildFailureTags(List<Tag> baseTags, Throwable throwable, boolean critical)
    {
        List<Tag> tags = new ArrayList<>(baseTags);
        tags.add(Tag.of("status",    "failure"));
        tags.add(Tag.of("exception", throwable.getClass().getSimpleName()));
        tags.add(Tag.of("message",   throwable.getMessage() != null
                ? throwable.getMessage() : "no_message"));
        tags.add(Tag.of("critical",  String.valueOf(critical)));
        return tags;
    }
}