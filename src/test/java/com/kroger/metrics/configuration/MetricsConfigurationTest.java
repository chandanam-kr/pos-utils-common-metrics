package com.kroger.metrics.configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    class AdditionalPrefixesTests
    {
        @Test
        void shouldReturnEmptyListByDefault()
        {
            assertThat(metricsConfiguration.getAdditionalPrefixes()).isEmpty();
        }

        @Test
        void shouldStoreAndReturnPrefixes()
        {
            metricsConfiguration.setAdditionalPrefixes(List.of("business", "custom"));

            assertThat(metricsConfiguration.getAdditionalPrefixes())
                    .containsExactly("business", "custom");
        }

        @Test
        void shouldReplacePrefixesOnSet()
        {
            metricsConfiguration.setAdditionalPrefixes(List.of("old"));
            metricsConfiguration.setAdditionalPrefixes(List.of("new.prefix"));

            assertThat(metricsConfiguration.getAdditionalPrefixes())
                    .containsExactly("new.prefix");
        }

        @Test
        void shouldUseEmptyListWhenNullSet()
        {
            metricsConfiguration.setAdditionalPrefixes(null);

            assertThat(metricsConfiguration.getAdditionalPrefixes()).isEmpty();
        }
    }

    @Nested
    class LogConfigTests
    {
        @Test
        void shouldNotThrow()
        {
            metricsConfiguration.setAdditionalPrefixes(List.of("business"));
            assertDoesNotThrow(() -> metricsConfiguration.logConfig());
        }

        @Test
        void shouldNotThrowWithEmptyPrefixes()
        {
            assertDoesNotThrow(() -> metricsConfiguration.logConfig());
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
        void shouldAllowJvmMemoryMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("jvm.memory.used")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowJvmThreadsMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("jvm.threads.live")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowHttpServerMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("http.server.requests")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowProcessCpuMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("process.cpu.usage")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowSystemCpuMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("system.cpu.count")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldAllowLogbackMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("logback.events")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldDenyUnknownMetric()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("not.allowed.metric")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        void shouldAllowAdditionalPrefixMetric()
        {
            metricsConfiguration.setAdditionalPrefixes(List.of("business.day"));
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("business.day.requests_total")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldDenyMetricNotMatchingAdditionalPrefix()
        {
            metricsConfiguration.setAdditionalPrefixes(List.of("business.day"));
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("other.service.metric")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        void shouldAllowMetricMatchingOneOfMultiplePrefixes()
        {
            metricsConfiguration.setAdditionalPrefixes(List.of("business.day", "store.ops"));
            MeterFilter filter = metricsConfiguration.meterFilter();

            assertThat(filter.accept(meterId("store.ops.inventory_total")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        void shouldReturnDenyForBlankMetricName()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();
            Meter.Id id = new Meter.Id("", Tags.empty(), null, null, Meter.Type.COUNTER);

            assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        void shouldReturnNeutralWhenFilterThrowsInternally()
        {
            MeterFilter filter = metricsConfiguration.meterFilter();
            Meter.Id id = new Meter.Id("unknown.metric", Tags.empty(), null, null, Meter.Type.COUNTER);

            assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.DENY);
        }

        private Meter.Id meterId(String name)
        {
            return new Meter.Id(name, Tags.empty(), null, null, Meter.Type.COUNTER);
        }
    }
}

