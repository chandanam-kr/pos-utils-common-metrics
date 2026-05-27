package com.kroger.metrics.configuration;

import com.kroger.metrics.aspect.MetricAspect;
import com.kroger.metrics.aspect.OnExceptionAspect;
import com.kroger.metrics.controller.MetricsController;
import com.kroger.metrics.service.MetricService;
import com.kroger.metrics.service.NoOpMetricService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto configuration for the metrics library.
 *
 * Disable via application.yml:
 *   metrics:
 *     enabled: false
 *
 * When disabled, MetricService still available as NoOp
 * to prevent injection failures in user code.
 */
@AutoConfiguration
@ConditionalOnClass({MeterRegistry.class, PrometheusMeterRegistry.class})
public class MetricsAutoConfiguration
{
    // ── Beans only when metrics.enabled=true ───────────────────────

    @Bean
    @ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MetricAspect.class)
    public MetricAspect metricAspect(MeterRegistry meterRegistry)
    {
        return new MetricAspect(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(OnExceptionAspect.class)
    public OnExceptionAspect onExceptionAspect(MeterRegistry meterRegistry,
                                               MetricAspect metricAspect)
    {
        return new OnExceptionAspect(meterRegistry, metricAspect);
    }

    @Bean
    @ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MetricsController.class)
    public MetricsController metricsController(PrometheusMeterRegistry registry)
    {
        return new MetricsController(registry);
    }

    // ── MetricService - ALWAYS available ───────────────────────────

    @Bean
    @ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MetricService.class)
    public MetricService metricService(MeterRegistry meterRegistry)
    {
        return new MetricService(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "metrics.enabled", havingValue = "false")
    @ConditionalOnMissingBean(MetricService.class)
    public MetricService noOpMetricService()
    {
        return new NoOpMetricService();
    }

    // ── EnableAspectJAutoProxy only when enabled ───────────────────

    @EnableAspectJAutoProxy
    @ConditionalOnProperty(name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    static class AspectConfig
    {
    }
}