package com.kroger.metrics.configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricsConfigurationTest
{
    private MetricsConfiguration configuration;

    @BeforeEach
    void setUp()
    {
        configuration = new MetricsConfiguration();
    }

    @Test
    void init_shouldBuildPrefixesFromCategoriesAndCustomCategories()
    {
        configuration.setUnfiltered(false);
        configuration.setCategories(List.of("jvm", "http", "business"));
        configuration.setCustomCategories(Map.of(
                "business", List.of("my.business", "order.processing"),
                "database", List.of("db", "jdbc")
        ));

        configuration.init();

        assertEquals(6, configuration.getEffectivePrefixes().size());
        assertTrue(configuration.getEffectivePrefixes().contains("my.business"));
        assertTrue(configuration.getEffectivePrefixes().contains("order.processing"));
        assertTrue(configuration.getEffectivePrefixes().contains("db"));
        assertTrue(configuration.getEffectivePrefixes().contains("jdbc"));
        assertTrue(configuration.getEffectivePrefixes().contains("jvm"));
        assertTrue(configuration.getEffectivePrefixes().contains("http"));

        assertEquals("business", configuration.getPrefixToCategory().get("my.business"));
        assertEquals("business", configuration.getPrefixToCategory().get("order.processing"));
        assertEquals("database", configuration.getPrefixToCategory().get("db"));
        assertEquals("database", configuration.getPrefixToCategory().get("jdbc"));
        assertEquals("jvm", configuration.getPrefixToCategory().get("jvm"));
        assertEquals("http", configuration.getPrefixToCategory().get("http"));
    }

    @Test
    void init_shouldSkipNormalCategoryIfAlsoPresentInCustomCategories()
    {
        configuration.setCategories(List.of("business", "jvm"));
        configuration.setCustomCategories(Map.of(
                "business", List.of("my.business")
        ));

        configuration.init();

        assertEquals(2, configuration.getEffectivePrefixes().size());
        assertTrue(configuration.getEffectivePrefixes().contains("my.business"));
        assertTrue(configuration.getEffectivePrefixes().contains("jvm"));
        assertFalse(configuration.getEffectivePrefixes().contains("business"));
    }

    @Test
    void init_shouldDoNothingWhenUnfiltered()
    {
        configuration.setUnfiltered(true);
        configuration.setCategories(List.of("jvm", "http"));
        configuration.setCustomCategories(Map.of("business", List.of("my.business")));

        configuration.init();

        assertTrue(configuration.getEffectivePrefixes().isEmpty());
        assertTrue(configuration.getPrefixToCategory().isEmpty());
    }

    @Test
    void meterFilter_accept_shouldReturnNeutralForAllowedMetric()
    {
        configuration.setUnfiltered(false);
        configuration.setCategories(List.of("jvm"));
        configuration.init();

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id id = new Meter.Id("jvm.memory.used", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.GAUGE);

        MeterFilterReply reply = filter.accept(id);

        assertEquals(MeterFilterReply.NEUTRAL, reply);
    }

    @Test
    void meterFilter_accept_shouldReturnDenyForDisallowedMetric()
    {
        configuration.setUnfiltered(false);
        configuration.setCategories(List.of("jvm"));
        configuration.init();

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id id = new Meter.Id("http.server.requests", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.TIMER);

        MeterFilterReply reply = filter.accept(id);

        assertEquals(MeterFilterReply.DENY, reply);
    }

    @Test
    void meterFilter_accept_shouldReturnNeutralWhenUnfiltered()
    {
        configuration.setUnfiltered(true);

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id id = new Meter.Id("anything.metric", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.COUNTER);

        MeterFilterReply reply = filter.accept(id);

        assertEquals(MeterFilterReply.NEUTRAL, reply);
    }

    @Test
    void meterFilter_map_shouldAddTypeTagFromSimpleCategory()
    {
        configuration.setCategories(List.of("jvm"));
        configuration.init();

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id original = new Meter.Id("jvm.memory.used", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.GAUGE);

        Meter.Id mapped = filter.map(original);

        assertEquals("jvm", mapped.getTag("type"));
    }

    @Test
    void meterFilter_map_shouldAddTypeTagFromCustomCategory()
    {
        configuration.setCustomCategories(Map.of(
                "business", List.of("my.business")
        ));
        configuration.init();

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id original = new Meter.Id("my.business.orders.created", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.COUNTER);

        Meter.Id mapped = filter.map(original);

        assertEquals("business", mapped.getTag("type"));
    }

    @Test
    void meterFilter_map_shouldDeriveTypeFromFirstSegmentWhenNoConfiguredMatch()
    {
        configuration.setUnfiltered(true);

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id original = new Meter.Id("http.server.requests", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.TIMER);

        Meter.Id mapped = filter.map(original);

        assertEquals("http", mapped.getTag("type"));
    }

    @Test
    void meterFilter_map_shouldReturnCustomWhenMetricHasNoDot()
    {
        configuration.setUnfiltered(true);

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id original = new Meter.Id("singletoken", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.COUNTER);

        Meter.Id mapped = filter.map(original);

        assertEquals("custom", mapped.getTag("type"));
    }

    @Test
    void meterFilter_map_shouldHandleUnderscoreMetricNames()
    {
        configuration.setCategories(List.of("jvm"));
        configuration.init();

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id original = new Meter.Id("jvm_memory_used", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.GAUGE);

        Meter.Id mapped = filter.map(original);

        assertEquals("jvm", mapped.getTag("type"));
    }

    @Test
    void meterFilter_map_shouldFallbackToDefaultTypeWhenNameBlank()
    {
        configuration.setUnfiltered(true);

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id original = new Meter.Id(" ", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.COUNTER);

        Meter.Id mapped = filter.map(original);

        assertEquals("custom", mapped.getTag("type"));
    }

    @Test
    void meterFilter_accept_shouldDenyBlankMetricName()
    {
        configuration.setCategories(List.of("jvm"));
        configuration.init();

        MeterFilter filter = configuration.metricsMeterFilter();
        Meter.Id id = new Meter.Id(" ", io.micrometer.core.instrument.Tags.empty(), null, null, Meter.Type.COUNTER);

        MeterFilterReply reply = filter.accept(id);

        assertEquals(MeterFilterReply.DENY, reply);
    }
}