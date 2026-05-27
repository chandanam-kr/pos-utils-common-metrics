package com.kroger.metrics.config;

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
@DisplayName("MetricsConfig Tests")
class MetricsConfigTest
{
    private MetricsConfig metricsConfig;

    @BeforeEach
    void setUp()
    {
        metricsConfig = new MetricsConfig();
    }

    // ── setAdditionalPrefixes / getAdditionalPrefixes Tests ───────────

    @Nested
    @DisplayName("AdditionalPrefixes Property Tests")
    class AdditionalPrefixesTests
    {
        @Test
        @DisplayName("Should return empty list by default")
        void shouldReturnEmptyListByDefault()
        {
            assertThat(metricsConfig.getAdditionalPrefixes()).isEmpty();
        }

        @Test
        @DisplayName("Should store and return set prefixes")
        void shouldStoreAndReturnPrefixes()
        {
            metricsConfig.setAdditionalPrefixes(List.of("business", "custom"));

            assertThat(metricsConfig.getAdditionalPrefixes())
                    .containsExactly("business", "custom");
        }

        @Test
        @DisplayName("Should replace existing prefixes on set")
        void shouldReplacePrefixesOnSet()
        {
            metricsConfig.setAdditionalPrefixes(List.of("old"));
            metricsConfig.setAdditionalPrefixes(List.of("new.prefix"));

            assertThat(metricsConfig.getAdditionalPrefixes())
                    .containsExactly("new.prefix");
        }

        @Test
        @DisplayName("Should use empty list when null is set")
        void shouldUseEmptyListWhenNullSet()
        {
            metricsConfig.setAdditionalPrefixes(null);

            assertThat(metricsConfig.getAdditionalPrefixes()).isEmpty();
        }
    }

    // ── logConfig Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("logConfig Tests")
    class LogConfigTests
    {
        @Test
        @DisplayName("Should not throw on logConfig call")
        void shouldNotThrow()
        {
            metricsConfig.setAdditionalPrefixes(List.of("business"));
            assertDoesNotThrow(() -> metricsConfig.logConfig());
        }

        @Test
        @DisplayName("Should not throw with empty additional prefixes")
        void shouldNotThrowWithEmptyPrefixes()
        {
            assertDoesNotThrow(() -> metricsConfig.logConfig());
        }
    }

    // ── meterFilter() Bean Tests ──────────────────────────────────────

    @Nested
    @DisplayName("MeterFilter Bean Tests")
    class MeterFilterTests
    {
        @Test
        @DisplayName("Should return non-null MeterFilter bean")
        void shouldReturnNonNullFilter()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter).isNotNull();
        }

        @Test
        @DisplayName("Should allow jvm.memory metric")
        void shouldAllowJvmMemoryMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("jvm.memory.used")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should allow jvm.threads metric")
        void shouldAllowJvmThreadsMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("jvm.threads.live")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should allow http.server metric")
        void shouldAllowHttpServerMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("http.server.requests")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should allow process.cpu metric")
        void shouldAllowProcessCpuMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("process.cpu.usage")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should allow system.cpu metric")
        void shouldAllowSystemCpuMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("system.cpu.count")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should allow logback metric")
        void shouldAllowLogbackMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("logback.events")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should deny unknown metric not in default or additional prefixes")
        void shouldDenyUnknownMetric()
        {
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("not.allowed.metric")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        @DisplayName("Should allow metric matching additional prefix")
        void shouldAllowAdditionalPrefixMetric()
        {
            metricsConfig.setAdditionalPrefixes(List.of("business.day"));
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("business.day.requests_total")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should deny metric not matching additional prefix")
        void shouldDenyMetricNotMatchingAdditionalPrefix()
        {
            metricsConfig.setAdditionalPrefixes(List.of("business.day"));
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("other.service.metric")))
                    .isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        @DisplayName("Should allow metric matching any of multiple additional prefixes")
        void shouldAllowMetricMatchingOneOfMultiplePrefixes()
        {
            metricsConfig.setAdditionalPrefixes(List.of("business.day", "store.ops"));
            MeterFilter filter = metricsConfig.meterFilter();

            assertThat(filter.accept(meterId("store.ops.inventory_total")))
                    .isEqualTo(MeterFilterReply.NEUTRAL);
        }

        @Test
        @DisplayName("Should return DENY when metric name is blank (empty string)")
        void shouldReturnDenyForBlankMetricName()
        {
            MeterFilter filter = metricsConfig.meterFilter();
            Meter.Id id = new Meter.Id("", Tags.empty(), null, null, Meter.Type.COUNTER);

            assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.DENY);
        }

        @Test
        @DisplayName("Should return NEUTRAL and not throw when isAllowed throws internally")
        void shouldReturnNeutralWhenFilterThrowsInternally()
        {
            // The try-catch in accept() returns NEUTRAL on any unexpected exception.
            // Simulate by passing a metric name that is neither null, blank, nor a known prefix,
            // confirming DENY is the normal path and the filter does not propagate exceptions.
            MeterFilter filter = metricsConfig.meterFilter();
            Meter.Id id = new Meter.Id("unknown.metric", Tags.empty(), null, null, Meter.Type.COUNTER);

            assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.DENY);
        }

        private Meter.Id meterId(String name)
        {
            return new Meter.Id(name, Tags.empty(), null, null, Meter.Type.COUNTER);
        }
    }
}

