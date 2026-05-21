package com.kroger.metrics.config;

import com.kroger.metrics.aspect.CustomMetricAspect;
import com.kroger.metrics.aspect.ExceptionMetricAspect;
import com.kroger.metrics.controller.MetricsController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableAspectJAutoProxy
@ConditionalOnClass({MeterRegistry.class, PrometheusMeterRegistry.class})
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CustomMetricAspect.class)
    public CustomMetricAspect customMetricAspect(MeterRegistry meterRegistry) {
        return new CustomMetricAspect(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ExceptionMetricAspect.class)
    public ExceptionMetricAspect exceptionMetricAspect(
            MeterRegistry meterRegistry,
            CustomMetricAspect customMetricAspect) {
        return new ExceptionMetricAspect(meterRegistry, customMetricAspect);
    }

    @Bean
    @ConditionalOnMissingBean(name = "meterFilter")
    public MetricsConfig metricsConfig() {
        return new MetricsConfig();
    }

    @Bean
    @ConditionalOnMissingBean(MetricsController.class)
    public MetricsController metricsController(PrometheusMeterRegistry registry) {
        return new MetricsController(registry);
    }
}