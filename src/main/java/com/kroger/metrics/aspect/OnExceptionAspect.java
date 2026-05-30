package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.OnException;
import com.kroger.metrics.annotation.Track;
import com.kroger.metrics.constants.MetricsConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.kroger.metrics.constants.MetricsConstants.*;

/**
 * Tracks exceptions for methods annotated with @OnException.
 * Never breaks user code - metric failures are caught and logged.
 */
@Slf4j
@Aspect
public class OnExceptionAspect
{
    private final MeterRegistry meterRegistry;
    private final MetricAspect  metricAspect;

    public OnExceptionAspect(@Lazy MeterRegistry meterRegistry, MetricAspect metricAspect)
    {
        this.meterRegistry = meterRegistry;
        this.metricAspect  = metricAspect;
    }

    @Around("@annotation(onException)")
    public Object capture(ProceedingJoinPoint joinPoint, OnException onException) throws Throwable
    {
        try
        {
            return joinPoint.proceed();
        }
        catch (Throwable throwable)
        {
            recordSafely(joinPoint, onException, throwable);
            throw throwable;
        }
    }

    private void recordSafely(ProceedingJoinPoint joinPoint, OnException onException, Throwable throwable)
    {
        try
        {
            if (isIgnored(throwable, onException.ignore())) return;

            List<Tag> baseTags = buildBaseTags(joinPoint, onException);

            findTrackMatch(throwable, onException.track())
                    .ifPresentOrElse(
                            track -> recordTracked(track, baseTags, throwable),
                            () -> recordDefault(onException, baseTags, throwable));
        }
        catch (Exception e)
        {
            log.warn(METRIC_TRACKING_FAILED, e.getMessage());
        }
    }

    private boolean isIgnored(Throwable throwable, Class<? extends Throwable>[] ignoreList)
    {
        return Arrays.stream(ignoreList).anyMatch(ex -> ex.isInstance(throwable));
    }

    private Optional<Track> findTrackMatch(Throwable throwable, Track[] tracks)
    {
        return Arrays.stream(tracks)
                .filter(t -> t.type().isInstance(throwable))
                .findFirst();
    }

    private void recordTracked(Track track, List<Tag> baseTags, Throwable throwable)
    {
        List<Tag> tags = buildFailureTags(baseTags, throwable, track.critical());
        meterRegistry.counter(track.metric() + TOTAL_SUFFIX, tags).increment();
    }

    private void recordDefault(OnException onException, List<Tag> baseTags, Throwable throwable)
    {
        if (onException.on().isEmpty()) return;

        String metricName = onException.on() + FAILURE_SUFFIX;
        List<Tag> tags    = buildFailureTags(baseTags, throwable, false);

        meterRegistry.counter(metricName + TOTAL_SUFFIX, tags).increment();
    }

    private List<Tag> buildBaseTags(ProceedingJoinPoint joinPoint, OnException onException)
    {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className          = joinPoint.getTarget().getClass().getSimpleName();
        String methodName         = signature.getName();
        Object[] args             = joinPoint.getArgs();
        Parameter[] parameters    = signature.getMethod().getParameters();

        return metricAspect.buildTags(onException.tags(),
                className, methodName, parameters, args);
    }

    private List<Tag> buildFailureTags(List<Tag> baseTags, Throwable throwable, boolean critical)
    {
        List<Tag> tags = new ArrayList<>(baseTags);
        tags.add(Tag.of(MetricsConstants.TAG_STATUS, MetricsConstants.STATUS_FAILURE));
        tags.add(Tag.of(MetricsConstants.TAG_EXCEPTION, throwable.getClass().getSimpleName()));
        tags.add(Tag.of(MetricsConstants.TAG_MESSAGE, throwable.getMessage() != null
                ? throwable.getMessage() : MetricsConstants.NO_MESSAGE));
        tags.add(Tag.of(MetricsConstants.TAG_CRITICAL, String.valueOf(critical)));
        return tags;
    }
}