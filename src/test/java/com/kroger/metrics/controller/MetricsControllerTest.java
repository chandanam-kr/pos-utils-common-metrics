package com.kroger.metrics.controller;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsController Tests")
class MetricsControllerTest
{
    @Mock
    private PrometheusMeterRegistry registry;

    private MetricsController controller;

    @BeforeEach
    void setUp()
    {
        controller = new MetricsController(registry);
    }

    // ── metrics() Endpoint Tests ──────────────────────────────────────

    @Nested
    @DisplayName("metrics() Endpoint Tests")
    class MetricsEndpointTests
    {
        @Test
        @DisplayName("Should return 200 OK on success")
        void shouldReturn200OnSuccess()
        {
            when(registry.scrape()).thenReturn("");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return 500 when scrape throws exception")
        void shouldReturn500OnError()
        {
            when(registry.scrape()).thenThrow(new RuntimeException("Prometheus down"));

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).contains("Error generating metrics");
        }

        @Test
        @DisplayName("Should return empty string for empty scrape output")
        void shouldReturnEmptyForNoMetrics()
        {
            when(registry.scrape()).thenReturn("");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should filter out comment lines starting with #")
        void shouldFilterCommentLines()
        {
            when(registry.scrape()).thenReturn(
                    "# HELP jvm_memory_used_bytes\n" +
                            "# TYPE jvm_memory_used_bytes gauge\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should filter out blank lines")
        void shouldFilterBlankLines()
        {
            when(registry.scrape()).thenReturn("\n\n\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).isEmpty();
        }
    }

    // ── Metric Renaming Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Metric Renaming Tests")
    class MetricRenamingTests
    {
        @Test
        @DisplayName("Should rename http_server_requests_seconds_count metric")
        void shouldRenameHttpRequestsCount()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{method=\"GET\"} 5.0\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("total_requests_completed");
            assertThat(response.getBody()).doesNotContain("http_server_requests_seconds_count");
        }

        @Test
        @DisplayName("Should rename jvm_memory_used_bytes metric")
        void shouldRenameJvmMemory()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("memory_currently_used_bytes");
            assertThat(response.getBody()).doesNotContain("jvm_memory_used_bytes{");
        }

        @Test
        @DisplayName("Should leave unknown metric names unchanged")
        void shouldLeaveUnknownMetricUnchanged()
        {
            when(registry.scrape()).thenReturn(
                    "custom_business_metric_total{env=\"prod\"} 42\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("custom_business_metric_total");
        }

        @Test
        @DisplayName("Should rename metric without tags (no braces)")
        void shouldRenameMetricWithoutTags()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_threads_live_threads 10\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("threads_currently_active");
        }
    }

    // ── Tag Value Renaming Tests ──────────────────────────────────────

    @Nested
    @DisplayName("Tag Value Renaming Tests")
    class TagValueRenamingTests
    {
        @Test
        @DisplayName("Should rename SUCCESS tag value to success_2xx")
        void shouldRenameSuccessTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{outcome=\"SUCCESS\"} 10\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("success_2xx");
            assertThat(response.getBody()).doesNotContain("=\"SUCCESS\"");
        }

        @Test
        @DisplayName("Should rename CLIENT_ERROR tag value")
        void shouldRenameClientErrorTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{outcome=\"CLIENT_ERROR\"} 3\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("client_error_4xx");
        }

        @Test
        @DisplayName("Should rename SERVER_ERROR tag value")
        void shouldRenameServerErrorTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"} 1\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("server_error_5xx");
        }

        @Test
        @DisplayName("Should rename Metaspace memory area tag value")
        void shouldRenameMetaspaceTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{id=\"Metaspace\"} 2048\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("nonheap_metaspace");
        }

        @Test
        @DisplayName("Should rename runnable thread state")
        void shouldRenameRunnableThreadState()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_threads_states_threads{state=\"runnable\"} 4\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("actively_running");
        }

        @Test
        @DisplayName("Should leave unknown tag values unchanged")
        void shouldLeaveUnknownTagValueUnchanged()
        {
            when(registry.scrape()).thenReturn(
                    "custom_metric{env=\"production\"} 1\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("=\"production\"");
        }
    }

    // ── Type & Timestamp Enrichment Tests ────────────────────────────

    @Nested
    @DisplayName("Type and Timestamp Enrichment Tests")
    class TypeAndTimestampEnrichmentTests
    {
        @Test
        @DisplayName("Should add type tag to metric line with existing tags")
        void shouldAddTypeTagToLineWithTags()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{method=\"GET\"} 5\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"http\"");
        }

        @Test
        @DisplayName("Should add timestamp tag to metric line")
        void shouldAddTimestampTagToLine()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{method=\"GET\"} 5\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("timestamp=\"");
        }

        @Test
        @DisplayName("Should use 'custom' type for unknown metrics")
        void shouldUseCustomTypeForUnknownMetric()
        {
            when(registry.scrape()).thenReturn(
                    "my_business_metric_total{env=\"prod\"} 1\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"custom\"");
        }

        @Test
        @DisplayName("Should add type=jvm for JVM memory metrics")
        void shouldAddJvmTypeForJvmMetrics()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"jvm\"");
        }

        @Test
        @DisplayName("Should add type=process for process CPU metric")
        void shouldAddProcessTypeForProcessMetric()
        {
            when(registry.scrape()).thenReturn(
                    "process_cpu_usage 0.05\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"process\"");
        }

        @Test
        @DisplayName("Should add type=logging for logback metric")
        void shouldAddLoggingTypeForLogback()
        {
            when(registry.scrape()).thenReturn(
                    "logback_events_total{level=\"info\"} 100\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"logging\"");
        }

        @Test
        @DisplayName("Should handle metric line without existing tags (no braces)")
        void shouldHandleLineWithoutBraces()
        {
            when(registry.scrape()).thenReturn(
                    "process_cpu_usage 0.05\n"
            );

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).contains("{");
            assertThat(body).contains("timestamp=\"");
            assertThat(body).contains("type=\"process\"");
        }

        @Test
        @DisplayName("Should inject tags before closing brace on line with existing tags")
        void shouldInjectBeforeClosingBrace()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_threads_live_threads{area=\"heap\"} 10\n"
            );

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            // The closing brace must be followed by the metric value
            assertThat(body).containsPattern("\\}\\s+10");
        }
    }

    // ── Full Pipeline Integration Tests ──────────────────────────────

    @Nested
    @DisplayName("Full Pipeline Tests")
    class FullPipelineTests
    {
        @Test
        @DisplayName("Should apply rename, tag rename, and enrichment in sequence")
        void shouldApplyAllTransformations()
        {
            when(registry.scrape()).thenReturn(
                    "# HELP http_server_requests_seconds_count Total requests\n" +
                            "http_server_requests_seconds_count{outcome=\"SUCCESS\"} 42\n"
            );

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            // comment filtered
            assertThat(body).doesNotContain("# HELP");
            // metric renamed
            assertThat(body).contains("total_requests_completed");
            // tag value renamed
            assertThat(body).contains("success_2xx");
            // type enriched
            assertThat(body).contains("type=\"http\"");
            // timestamp enriched
            assertThat(body).contains("timestamp=\"");
        }

        @Test
        @DisplayName("Should produce one output line per valid input line")
        void shouldProduceOneLinePerValidLine()
        {
            when(registry.scrape()).thenReturn(
                    "# comment\n" +
                            "metric_a{x=\"1\"} 1\n" +
                            "\n" +
                            "metric_b{x=\"2\"} 2\n"
            );

            ResponseEntity<String> response = controller.metrics();

            String[] lines = response.getBody().split("\n");
            assertThat(lines).hasSize(2);
        }
    }
}

