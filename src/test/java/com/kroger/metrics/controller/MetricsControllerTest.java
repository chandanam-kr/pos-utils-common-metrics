package com.kroger.metrics.controller;

import com.kroger.metrics.configuration.MetricsConfiguration;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest
{
    @Mock
    private PrometheusMeterRegistry registry;

    private MetricsConfiguration metricsConfig;
    private MetricsController controller;

    @BeforeEach
    void setUp()
    {
        metricsConfig = new MetricsConfiguration();
        controller = new MetricsController(registry, metricsConfig);
    }

    @Nested
    class MetricsEndpointTests
    {
        @Test
        void shouldReturn200OnSuccess()
        {
            when(registry.scrape()).thenReturn("");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void shouldReturn500OnError()
        {
            when(registry.scrape()).thenThrow(new RuntimeException("Prometheus down"));

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).contains("Error generating metrics");
        }

        @Test
        void shouldReturnEmptyForNoMetrics()
        {
            when(registry.scrape()).thenReturn("");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        void shouldFilterCommentLines()
        {
            when(registry.scrape()).thenReturn("# HELP jvm_memory_used_bytes\n" + "# TYPE jvm_memory_used_bytes gauge\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        void shouldFilterBlankLines()
        {
            when(registry.scrape()).thenReturn("\n\n\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    class TypeTagFromCategoriesTests
    {
        @Test
        void shouldAddTypeFromSimpleCategory()
        {
            metricsConfig.setCategories(List.of("http"));
            metricsConfig.init();

            when(registry.scrape()).thenReturn("http_server_requests_seconds_count{method=\"GET\"} 5\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"http\"");
        }

        @Test
        void shouldAddTypeFromCustomCategory()
        {
            metricsConfig.setCustomCategories(Map.of("business-day-service", List.of("ISA")));
            metricsConfig.init();

            when(registry.scrape()).thenReturn("ISA_BUSINESS_DAY_LIST_total{dltId=\"123\"} 1\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"business-day-service\"");
        }

        @Test
        void shouldAddJvmTypeWhenJvmConfigured()
        {
            metricsConfig.setCategories(List.of("jvm"));
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"jvm\"");
        }

        @Test
        void shouldFallbackToFirstSegmentWhenNoCategoryMatches()
        {
            when(registry.scrape()).thenReturn(
                    "my_business_metric_total{env=\"prod\"} 1\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"my\"");
        }

        @Test
        void shouldFallbackToCustomForSingleTokenMetric()
        {
            when(registry.scrape()).thenReturn("singletoken 1\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"custom\"");
        }

        @Test
        void shouldPreferCustomCategoryOverSimpleCategory()
        {
            metricsConfig.setCategories(List.of("jvm"));
            metricsConfig.setCustomCategories(Map.of("runtime", List.of("jvm.memory")));
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"runtime\"");
        }
    }

    @Nested
    class TimestampEnrichmentTests
    {
        @Test
        void shouldAddTimestampTagToLine()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{method=\"GET\"} 5\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("timestamp=\"");
        }

        @Test
        void shouldAddTimestampToLineWithoutTags()
        {
            when(registry.scrape()).thenReturn("process_cpu_usage 0.05\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("timestamp=\"");
        }

        @Test
        void shouldHaveSameTimestampForAllLinesInOneScrape()
        {
            when(registry.scrape()).thenReturn(
                    "metric_a{x=\"1\"} 1\nmetric_b{x=\"2\"} 2\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            String[] lines = body.split("\n");
            String firstTimestamp = extractTimestamp(lines[0]);
            String secondTimestamp = extractTimestamp(lines[1]);

            assertThat(firstTimestamp).isEqualTo(secondTimestamp);
        }

        private String extractTimestamp(String line)
        {
            int start = line.indexOf("timestamp=\"") + "timestamp=\"".length();
            int end = line.indexOf("\"", start);
            return line.substring(start, end);
        }
    }

    @Nested
    class TagInjectionTests
    {
        @Test
        void shouldInjectTagsIntoExistingTagBlock()
        {
            metricsConfig.setCategories(List.of("jvm"));
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "jvm_threads_live_threads{area=\"heap\"} 10\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).contains("area=\"heap\"");
            assertThat(body).contains("type=\"jvm\"");
            assertThat(body).contains("timestamp=\"");
            assertThat(body).containsPattern("\\}\\s+10");
        }

        @Test
        void shouldCreateNewTagBlockForLineWithoutTags()
        {
            when(registry.scrape()).thenReturn("process_cpu_usage 0.05\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).contains("{");
            assertThat(body).contains("}");
            assertThat(body).contains("timestamp=\"");
            assertThat(body).contains("type=\"process\"");
        }

        @Test
        void shouldPreserveOriginalTagsWhenInjecting()
        {
            metricsConfig.setCategories(List.of("http"));
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{method=\"GET\",status=\"200\"} 42\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).contains("method=\"GET\"");
            assertThat(body).contains("status=\"200\"");
            assertThat(body).contains("type=\"http\"");
        }
    }

    @Nested
    class FullPipelineTests
    {
        @Test
        void shouldApplyAllTransformations()
        {
            metricsConfig.setCategories(List.of("http"));
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "# HELP http_server_requests_seconds_count Total requests\n" +
                            "http_server_requests_seconds_count{outcome=\"SUCCESS\"} 42\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).doesNotContain("# HELP");
            assertThat(body).contains("http_server_requests_seconds_count");
            assertThat(body).contains("outcome=\"SUCCESS\"");
            assertThat(body).contains("type=\"http\"");
            assertThat(body).contains("timestamp=\"");
        }

        @Test
        void shouldProduceOneLinePerValidLine()
        {
            when(registry.scrape()).thenReturn(
                    "# comment\n" + "metric_a{x=\"1\"} 1\n" +
                            "\n" + "metric_b{x=\"2\"} 2\n");

            ResponseEntity<String> response = controller.metrics();

            String[] lines = response.getBody().split("\n");
            assertThat(lines).hasSize(2);
        }

        @Test
        void shouldHandleMixedMetricFormats()
        {
            metricsConfig.setCategories(List.of("jvm", "process"));
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n" +
                            "process_cpu_usage 0.05\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).contains("type=\"jvm\"");
            assertThat(body).contains("type=\"process\"");
            assertThat(body).contains("timestamp=\"");
        }

        @Test
        void shouldHandleEmptyCustomCategoriesGracefully()
        {
            metricsConfig.init();

            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"jvm\"");
            assertThat(response.getBody()).contains("timestamp=\"");
        }
    }
}