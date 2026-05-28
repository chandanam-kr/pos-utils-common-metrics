package com.kroger.metrics.configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class MetricsConfigurationTest
{
    private MetricsConfiguration metricsConfiguration;

    @BeforeEach
    void setUp()
    {
        metricsConfiguration = new MetricsConfiguration();
    }

    @Nested
    class CategoriesTests
    {
        @Test
        void shouldReturnEmptyListByDefault()
        {
            assertThat(metricsConfiguration.getCategories()).isEmpty();
        }

        @Test
        void shouldStoreAndReturnCategories()
        {
            metricsConfiguration.setCategories(List.of("http", "jvm"));

            assertThat(metricsConfiguration.getCategories())
                    .containsExactly("http", "jvm");
        }

        @Test
        void shouldReplaceCategoriesOnSet()
        {
            metricsConfiguration.setCategories(List.of("old"));
            metricsConfiguration.setCategories(List.of("http", "system"));

            assertThat(metricsConfiguration.getCategories())
                    .containsExactly("http", "system");
        }
    }

    @Nested
    class CustomCategoriesTests
    {
        @Test
        void shouldReturnEmptyMapByDefault()
        {
            assertThat(metricsConfiguration.getCustomCategories()).isEmpty();
        }

        @Test
        void shouldStoreAndReturnCustomCategories()
        {
            metricsConfiguration.setCustomCategories(Map.of(
                    "business-day-service", List.of("ISA")));

            assertThat(metricsConfiguration.getCustomCategories())
                    .containsEntry("business-day-service", List.of("ISA"));
        }

        @Test
        void shouldReplaceCustomCategoriesOnSet()
        {
            metricsConfiguration.setCustomCategories(Map.of("old", List.of("oldPrefix")));
            metricsConfiguration.setCustomCategories(Map.of("new", List.of("new.prefix")));

            assertThat(metricsConfiguration.getCustomCategories())
                    .containsOnlyKeys("new")
                    .containsEntry("new", List.of("new.prefix"));
        }
    }

    @Nested
    class InitTests
    {
        @Test
        void shouldNotThrowWithEmptyConfig()
        {
            assertDoesNotThrow(() -> metricsConfiguration.init());
        }

        @Test
        void shouldNotThrowWithCategoriesOnly()
        {
            metricsConfiguration.setCategories(List.of("http", "jvm"));
            assertDoesNotThrow(() -> metricsConfiguration.init());
        }

        @Test
        void shouldNotThrowWithCustomCategoriesOnly()
        {
            metricsConfiguration.setCustomCategories(Map.of("business", List.of("ISA")));
            assertDoesNotThrow(() -> metricsConfiguration.init());
        }

        @Test
        void shouldNotThrowInUnfilteredMode()
        {
            metricsConfiguration.setUnfiltered(true);
            assertDoesNotThrow(() -> metricsConfiguration.init());
        }

        @Test
        void shouldPopulateEffectivePrefixesFromCategories()
        {
            metricsConfiguration.setCategories(List.of("http", "jvm"));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getEffectivePrefixes())
                    .containsExactlyInAnyOrder("http", "jvm");
        }

        @Test
        void shouldPopulateEffectivePrefixesFromCustomCategories()
        {
            metricsConfiguration.setCustomCategories(Map.of(
                    "business", List.of("ISA", "orders")));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getEffectivePrefixes())
                    .containsExactlyInAnyOrder("ISA", "orders");
        }

        @Test
        void shouldMergeCategoriesAndCustomCategories()
        {
            metricsConfiguration.setCategories(List.of("http", "jvm"));
            metricsConfiguration.setCustomCategories(Map.of("business", List.of("ISA")));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getEffectivePrefixes())
                    .containsExactlyInAnyOrder("http", "jvm", "ISA");
        }

        @Test
        void shouldGiveCustomCategoriesPrecedenceOverSimpleCategories()
        {
            metricsConfiguration.setCategories(List.of("jvm"));
            metricsConfiguration.setCustomCategories(Map.of("jvm", List.of("jvm.memory")));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getEffectivePrefixes())
                    .containsExactly("jvm.memory");
        }

        @Test
        void shouldSkipCustomCategoryWithEmptyPrefixes()
        {
            metricsConfiguration.setCustomCategories(Map.of("empty", List.of()));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getEffectivePrefixes()).isEmpty();
        }

        @Test
        void shouldBuildPrefixToCategoryMap()
        {
            metricsConfiguration.setCategories(List.of("http"));
            metricsConfiguration.setCustomCategories(Map.of("business", List.of("ISA")));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getPrefixToCategory())
                    .containsEntry("http", "http")
                    .containsEntry("ISA", "business");
        }
    }

    @Nested
    class MeterFilterTests
    {
        @Test
        void shouldReturnNonNullFilter()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter).isNotNull();
        }

        @Test
        void shouldAllowMetricMatchingSimpleCategory()
        {
            metricsConfiguration.setCategories(List.of("http"));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("http.server.requests")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowMetricMatchingCustomCategoryPrefix()
        {
            metricsConfiguration.setCustomCategories(Map.of("business", List.of("ISA")));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("ISA.business.day.list")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowJvmMetricWhenJvmConfigured()
        {
            metricsConfiguration.setCategories(List.of("jvm"));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("jvm.memory.used")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldDenyMetricNotMatchingAnyCategory()
        {
            metricsConfiguration.setCategories(List.of("http"));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("jvm.memory.used")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        void shouldDenyAllMetricsWhenNoCategoriesConfigured()
        {
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("jvm.memory.used")))
                    .isEqualTo(MeterFilterReply.DENY);
            assertThat(filter.accept(meterId("http.server.requests")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        void shouldAllowAllMetricsInUnfilteredMode()
        {
            metricsConfiguration.setUnfiltered(true);
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("anything.at.all")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
            assertThat(filter.accept(meterId("random.metric")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldDenyBlankMetricName()
        {
            metricsConfiguration.setCategories(List.of("http"));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            Meter.Id id = new Meter.Id("", Tags.empty(), null, null, Meter.Type.COUNTER);
            assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        void shouldAllowMetricMatchingOneOfMultiplePrefixes()
        {
            metricsConfiguration.setCustomCategories(Map.of(
                    "business", List.of("ISA", "orders", "payments")));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("payments.gateway.charge")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldDenyMetricNotMatchingAnyPrefix()
        {
            metricsConfiguration.setCustomCategories(Map.of("business", List.of("ISA")));
            metricsConfiguration.init();
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("other.service.metric")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        private Meter.Id meterId(String name)
        {
            return new Meter.Id(name, Tags.empty(), null, null, Meter.Type.COUNTER);
        }
    }

    @Nested
    class CommonTagsCustomizerTests
    {
        @Test
        void shouldReturnNonNullCustomizer()
        {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    metricsConfiguration.commonTagsCustomizer("isa-service");

            assertThat(customizer).isNotNull();
        }

        @Test
        void shouldAddAppTagWithProvidedName()
        {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    metricsConfiguration.commonTagsCustomizer("isa-service");
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

            customizer.customize(registry);
            registry.counter("test.counter").increment();

            assertThat(registry.scrape()).contains("app=\"isa-service\"");
        }

        @Test
        void shouldDefaultToUnknownWhenAppNameIsNull()
        {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    metricsConfiguration.commonTagsCustomizer(null);
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

            customizer.customize(registry);
            registry.counter("test.counter").increment();

            assertThat(registry.scrape()).contains("app=\"unknown\"");
        }

        @Test
        void shouldDefaultToUnknownWhenAppNameIsBlank()
        {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    metricsConfiguration.commonTagsCustomizer("   ");
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

            customizer.customize(registry);
            registry.counter("test.counter").increment();

            assertThat(registry.scrape()).contains("app=\"unknown\"");
        }

        @Test
        void shouldDefaultToUnknownWhenAppNameIsEmpty()
        {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    metricsConfiguration.commonTagsCustomizer("");
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

            customizer.customize(registry);
            registry.counter("test.counter").increment();

            assertThat(registry.scrape()).contains("app=\"unknown\"");
        }

        @Test
        void shouldApplyAppTagToAllMetrics()
        {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    metricsConfiguration.commonTagsCustomizer("my-service");
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

            customizer.customize(registry);
            registry.counter("counter.one").increment();
            registry.counter("counter.two").increment();

            String scrape = registry.scrape();
            assertThat(scrape).contains("counter_one_total{app=\"my-service\"");
            assertThat(scrape).contains("counter_two_total{app=\"my-service\"");
        }
    }

    @Nested
    class UnfilteredModeTests
    {
        @Test
        void shouldDefaultToFalse()
        {
            assertThat(metricsConfiguration.isUnfiltered()).isFalse();
        }

        @Test
        void shouldStoreUnfilteredTrue()
        {
            metricsConfiguration.setUnfiltered(true);

            assertThat(metricsConfiguration.isUnfiltered()).isTrue();
        }

        @Test
        void shouldSkipPrefixComputationInUnfilteredMode()
        {
            metricsConfiguration.setUnfiltered(true);
            metricsConfiguration.setCategories(List.of("http", "jvm"));
            metricsConfiguration.init();

            assertThat(metricsConfiguration.getEffectivePrefixes()).isEmpty();
            assertThat(metricsConfiguration.getPrefixToCategory()).isEmpty();
        }
    }
}