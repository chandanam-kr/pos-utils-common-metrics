package com.kroger.metrics.configuration;

import com.kroger.metrics.aspect.MetricAspect;
import com.kroger.metrics.aspect.OnExceptionAspect;
import com.kroger.metrics.service.MetricService;
import com.kroger.metrics.service.NoOpMetricService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAutoConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
            .withBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(PrometheusMeterRegistry.class, () -> null);

    @Test
    void shouldCreateBeansWhenMetricsEnabled()
    {
        contextRunner
                .withPropertyValues("metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MetricAspect.class);
                    assertThat(context).hasSingleBean(OnExceptionAspect.class);
                    assertThat(context).hasSingleBean(MetricService.class);
                    assertThat(context).doesNotHaveBean(NoOpMetricService.class);
                });
    }

    @Test
    void shouldCreateBeansWhenMetricsEnabledPropertyMissing()
    {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(MetricAspect.class);
                    assertThat(context).hasSingleBean(OnExceptionAspect.class);
                    assertThat(context).hasSingleBean(MetricService.class);
                });
    }

    @Test
    void shouldCreateNoOpMetricServiceWhenMetricsDisabled()
    {
        contextRunner
                .withPropertyValues("metrics.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MetricAspect.class);
                    assertThat(context).doesNotHaveBean(OnExceptionAspect.class);
                    assertThat(context).hasSingleBean(MetricService.class);
                    assertThat(context.getBean(MetricService.class)).isInstanceOf(NoOpMetricService.class);
                });
    }

    @Test
    void shouldBackOffWhenMetricServiceAlreadyProvided()
    {
        contextRunner
                .withPropertyValues("metrics.enabled=true")
                .withBean(MetricService.class, () -> org.mockito.Mockito.mock(MetricService.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(MetricService.class);
                });
    }

    @Test
    void shouldBackOffWhenMetricAspectAlreadyProvided()
    {
        contextRunner
                .withPropertyValues("metrics.enabled=true")
                .withBean(MetricAspect.class,
                        () -> new MetricAspect(new SimpleMeterRegistry()))
                .run(context -> {
                    assertThat(context).hasSingleBean(MetricAspect.class);
                });
    }

    @Test
    void shouldBackOffWhenOnExceptionAspectAlreadyProvided()
    {
        contextRunner
                .withPropertyValues("metrics.enabled=true")
                .withBean(MetricAspect.class,
                        () -> new MetricAspect(new SimpleMeterRegistry()))
                .withBean(OnExceptionAspect.class,
                        () -> new OnExceptionAspect(new SimpleMeterRegistry(),
                                new MetricAspect(new SimpleMeterRegistry())))
                .run(context -> {
                    assertThat(context).hasSingleBean(OnExceptionAspect.class);
                });
    }
}