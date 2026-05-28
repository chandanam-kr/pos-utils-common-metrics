package com.kroger.metrics.configuration;

import com.kroger.metrics.aspect.MetricAspect;
import com.kroger.metrics.aspect.OnExceptionAspect;
import com.kroger.metrics.controller.MetricsController;
import com.kroger.metrics.service.MetricService;
import com.kroger.metrics.service.NoOpMetricService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAutoConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockRegistryConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    MetricsConfiguration.class));

    @Configuration
    static class MockRegistryConfig
    {
        @Bean
        @Primary
        public PrometheusMeterRegistry prometheusMeterRegistry()
        {
            return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        }
    }

    @Nested
    class MetricsEnabledTests
    {
        @Test
        void metricAspectBeanPresent()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasSingleBean(MetricAspect.class));
        }

        @Test
        void onExceptionAspectBeanPresent()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasSingleBean(OnExceptionAspect.class));
        }

        @Test
        void metricsControllerBeanPresent()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasSingleBean(MetricsController.class));
        }

        @Test
        void metricServiceBeanPresent()
        {
            contextRunner.run(ctx ->
            {
                assertThat(ctx).hasSingleBean(MetricService.class);
                assertThat(ctx.getBean(MetricService.class)).isNotInstanceOf(NoOpMetricService.class);
            });
        }

        @Test
        void metricsConfigurationBeanPresent()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasSingleBean(MetricsConfiguration.class));
        }

        @Test
        void commonTagsCustomizerBeanPresent()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasBean("commonTagsCustomizer"));
        }

        @Test
        void noOpMetricServiceBeanAbsent()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).doesNotHaveBean(NoOpMetricService.class));
        }
    }

    @Nested
    class MetricsEnabledExplicitTests
    {
        @Test
        void metricAspectBeanPresentExplicit()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=true")
                    .run(ctx ->
                            assertThat(ctx).hasSingleBean(MetricAspect.class));
        }

        @Test
        void metricServiceBeanPresentExplicit()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=true")
                    .run(ctx ->
                            assertThat(ctx).hasSingleBean(MetricService.class));
        }
    }

    @Nested
    class MetricsDisabledTests
    {
        @Test
        void metricAspectBeanAbsent()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).doesNotHaveBean(MetricAspect.class));
        }

        @Test
        void onExceptionAspectBeanAbsent()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).doesNotHaveBean(OnExceptionAspect.class));
        }

        @Test
        void metricsControllerBeanAbsent()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).doesNotHaveBean(MetricsController.class));
        }

        @Test
        void metricServiceIsNoOp()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=false")
                    .run(ctx ->
                    {
                        assertThat(ctx).hasSingleBean(MetricService.class);
                        assertThat(ctx.getBean(MetricService.class)).isInstanceOf(NoOpMetricService.class);
                    });
        }

        @Test
        void onlyOneMetricServiceWhenDisabled()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).hasSingleBean(MetricService.class));
        }
    }

    @Nested
    class UserProvidedBeanTests
    {
        @Test
        void userProvidedMetricAspectPreventsAutoConfig()
        {
            contextRunner
                    .withUserConfiguration(UserMetricAspectConfig.class)
                    .run(ctx ->
                    {
                        assertThat(ctx).hasSingleBean(MetricAspect.class);
                        assertThat(ctx.getBean(MetricAspect.class))
                                .isSameAs(ctx.getBean("customMetricAspect"));
                    });
        }

        @Test
        void userProvidedMetricServicePreventsAutoConfig()
        {
            contextRunner
                    .withUserConfiguration(UserMetricServiceConfig.class)
                    .run(ctx ->
                    {
                        assertThat(ctx).hasSingleBean(MetricService.class);
                        assertThat(ctx.getBean(MetricService.class))
                                .isSameAs(ctx.getBean("customMetricService"));
                    });
        }

        @Test
        void userProvidedMetricsControllerPreventsAutoConfig()
        {
            contextRunner
                    .withUserConfiguration(UserMetricsControllerConfig.class)
                    .run(ctx ->
                    {
                        assertThat(ctx).hasSingleBean(MetricsController.class);
                        assertThat(ctx.getBean(MetricsController.class))
                                .isSameAs(ctx.getBean("customMetricsController"));
                    });
        }

        @Configuration
        static class UserMetricAspectConfig
        {
            @Bean("customMetricAspect")
            public MetricAspect customMetricAspect(MeterRegistry meterRegistry)
            {
                return new MetricAspect(meterRegistry);
            }
        }

        @Configuration
        static class UserMetricServiceConfig
        {
            @Bean("customMetricService")
            public MetricService customMetricService(MeterRegistry meterRegistry)
            {
                return new MetricService(meterRegistry);
            }
        }

        @Configuration
        static class UserMetricsControllerConfig
        {
            @Bean("customMetricsController")
            public MetricsController customMetricsController(
                    PrometheusMeterRegistry registry,
                    MetricsConfiguration metricsConfig)
            {
                return new MetricsController(registry, metricsConfig);
            }
        }
    }

    @Nested
    class ContextLoadTests
    {
        @Test
        void contextStartsWithDefaults()
        {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasNotFailed());
        }

        @Test
        void contextStartsWhenDisabled()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).hasNotFailed());
        }

        @Test
        void contextStartsWhenExplicitlyEnabled()
        {
            contextRunner
                    .withPropertyValues("metrics.enabled=true")
                    .run(ctx ->
                            assertThat(ctx).hasNotFailed());
        }

        @Test
        void contextStartsWithCategoriesConfigured()
        {
            contextRunner
                    .withPropertyValues(
                            "info.app.name=test-service",
                            "metrics.categories=http,jvm")
                    .run(ctx ->
                            assertThat(ctx).hasNotFailed());
        }
    }

    @Nested
    class CommonTagsTests
    {
        @Test
        void appTagResolvedFromInfoAppName()
        {
            contextRunner
                    .withPropertyValues("info.app.name=isa-service")
                    .run(ctx ->
                    {
                        PrometheusMeterRegistry registry = ctx.getBean(PrometheusMeterRegistry.class);
                        MeterRegistryCustomizer<MeterRegistry> customizer =
                                ctx.getBean("commonTagsCustomizer", MeterRegistryCustomizer.class);

                        customizer.customize(registry);
                        registry.counter("test.counter").increment();

                        assertThat(registry.scrape()).contains("app=\"isa-service\"");
                    });
        }

        @Test
        void appTagDefaultsToUnknownWhenInfoAppNameMissing()
        {
            contextRunner.run(ctx ->
            {
                PrometheusMeterRegistry registry = ctx.getBean(PrometheusMeterRegistry.class);
                MeterRegistryCustomizer<MeterRegistry> customizer =
                        ctx.getBean("commonTagsCustomizer", MeterRegistryCustomizer.class);

                customizer.customize(registry);
                registry.counter("test.counter").increment();

                assertThat(registry.scrape()).contains("app=\"unknown\"");
            });
        }

        @Test
        void appTagAppliedToAllMetrics()
        {
            contextRunner
                    .withPropertyValues("info.app.name=my-app")
                    .run(ctx ->
                    {
                        PrometheusMeterRegistry registry = ctx.getBean(PrometheusMeterRegistry.class);
                        MeterRegistryCustomizer<MeterRegistry> customizer =
                                ctx.getBean("commonTagsCustomizer", MeterRegistryCustomizer.class);

                        customizer.customize(registry);
                        registry.counter("counter.one").increment();
                        registry.counter("counter.two").increment();

                        String scrape = registry.scrape();
                        assertThat(scrape).contains("counter_one_total{app=\"my-app\"");
                        assertThat(scrape).contains("counter_two_total{app=\"my-app\"");
                    });
        }
    }

    @Nested
    class UnfilteredModeTests
    {
        @Test
        void unfilteredModeBeansPresent()
        {
            contextRunner
                    .withPropertyValues("metrics.unfiltered=true")
                    .run(ctx ->
                    {
                        assertThat(ctx).hasSingleBean(MetricAspect.class);
                        assertThat(ctx).hasSingleBean(MetricsController.class);
                        assertThat(ctx).hasSingleBean(MetricsConfiguration.class);
                    });
        }

        @Test
        void unfilteredModeContextLoads()
        {
            contextRunner
                    .withPropertyValues("metrics.unfiltered=true")
                    .run(ctx ->
                            assertThat(ctx).hasNotFailed());
        }
    }
}