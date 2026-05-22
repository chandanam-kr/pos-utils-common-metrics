package com.kroger.metrics.config;

import com.kroger.metrics.aspect.MetricAspect;
import com.kroger.metrics.aspect.OnExceptionAspect;
import com.kroger.metrics.controller.MetricsController;
import com.kroger.metrics.service.MetricService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Registers all aspects, services and controllers automatically.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(MetricsConfig.class)
@ConditionalOnClass({MeterRegistry.class, PrometheusMeterRegistry.class})
public class MetricsAutoConfiguration
{
    @Bean
    @ConditionalOnMissingBean(MetricAspect.class)
    public MetricAspect metricAspect(MeterRegistry meterRegistry)
    {
        return new MetricAspect(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(OnExceptionAspect.class)
    public OnExceptionAspect onExceptionAspect(MeterRegistry meterRegistry, MetricAspect metricAspect)
    {
        return new OnExceptionAspect(meterRegistry, metricAspect);
    }

    @Bean
    @ConditionalOnMissingBean(MetricService.class)
    public MetricService metricService(MeterRegistry meterRegistry)
    {
        return new MetricService(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(MetricsController.class)
    public MetricsController metricsController(PrometheusMeterRegistry registry)
    {
        return new MetricsController(registry);
    }
}