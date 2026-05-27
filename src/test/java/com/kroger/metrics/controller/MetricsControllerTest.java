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
            when(registry.scrape()).thenReturn("# HELP jvm_memory_used_bytes\n" +
                    "# TYPE jvm_memory_used_bytes gauge\n");

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
    class MetricRenamingTests
    {
        @Test
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
        void shouldLeaveUnknownMetricUnchanged()
        {
            when(registry.scrape()).thenReturn(
                    "custom_business_metric_total{env=\"prod\"} 42\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("custom_business_metric_total");
        }

        @Test
        void shouldRenameMetricWithoutTags()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_threads_live_threads 10\n"
            );

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("threads_currently_active");
        }
    }

    @Nested
    class TagValueRenamingTests
    {
        @Test
        void shouldRenameSuccessTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{outcome=\"SUCCESS\"} 10\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("success_2xx");
            assertThat(response.getBody()).doesNotContain("=\"SUCCESS\"");
        }

        @Test
        void shouldRenameClientErrorTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{outcome=\"CLIENT_ERROR\"} 3\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("client_error_4xx");
        }

        @Test
        void shouldRenameServerErrorTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"} 1\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("server_error_5xx");
        }

        @Test
        void shouldRenameMetaspaceTagValue()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{id=\"Metaspace\"} 2048\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("nonheap_metaspace");
        }

        @Test
        void shouldRenameRunnableThreadState()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_threads_states_threads{state=\"runnable\"} 4\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("actively_running");
        }

        @Test
        void shouldLeaveUnknownTagValueUnchanged()
        {
            when(registry.scrape()).thenReturn("custom_metric{env=\"production\"} 1\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("=\"production\"");
        }
    }

    @Nested
    class TypeAndTimestampEnrichmentTests
    {
        @Test
        void shouldAddTypeTagToLineWithTags()
        {
            when(registry.scrape()).thenReturn(
                    "http_server_requests_seconds_count{method=\"GET\"} 5\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"http\"");
        }

        @Test
        void shouldAddTimestampTagToLine()
        {
            when(registry.scrape()).thenReturn("http_server_requests_seconds_count{method=\"GET\"} 5\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("timestamp=\"");
        }

        @Test
        void shouldUseCustomTypeForUnknownMetric()
        {
            when(registry.scrape()).thenReturn("my_business_metric_total{env=\"prod\"} 1\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"custom\"");
        }

        @Test
        void shouldAddJvmTypeForJvmMetrics()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_memory_used_bytes{area=\"heap\"} 1024\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"jvm\"");
        }

        @Test
        void shouldAddProcessTypeForProcessMetric()
        {
            when(registry.scrape()).thenReturn("process_cpu_usage 0.05\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"process\"");
        }

        @Test
        void shouldAddLoggingTypeForLogback()
        {
            when(registry.scrape()).thenReturn("logback_events_total{level=\"info\"} 100\n");

            ResponseEntity<String> response = controller.metrics();

            assertThat(response.getBody()).contains("type=\"logging\"");
        }

        @Test
        void shouldHandleLineWithoutBraces()
        {
            when(registry.scrape()).thenReturn("process_cpu_usage 0.05\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).contains("{");
            assertThat(body).contains("timestamp=\"");
            assertThat(body).contains("type=\"process\"");
        }

        @Test
        void shouldInjectBeforeClosingBrace()
        {
            when(registry.scrape()).thenReturn(
                    "jvm_threads_live_threads{area=\"heap\"} 10\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).containsPattern("\\}\\s+10");
        }
    }

    @Nested
    class FullPipelineTests
    {
        @Test
        void shouldApplyAllTransformations()
        {
            when(registry.scrape()).thenReturn(
                    "# HELP http_server_requests_seconds_count Total requests\n" +
                            "http_server_requests_seconds_count{outcome=\"SUCCESS\"} 42\n");

            ResponseEntity<String> response = controller.metrics();

            String body = response.getBody();
            assertThat(body).doesNotContain("# HELP");
            assertThat(body).contains("total_requests_completed");
            assertThat(body).contains("success_2xx");
            assertThat(body).contains("type=\"http\"");
            assertThat(body).contains("timestamp=\"");
        }

        @Test
        void shouldProduceOneLinePerValidLine()
        {
            when(registry.scrape()).thenReturn("# comment\n" + "metric_a{x=\"1\"} 1\n" +
                            "\n" + "metric_b{x=\"2\"} 2\n");

            ResponseEntity<String> response = controller.metrics();

            String[] lines = response.getBody().split("\n");
            assertThat(lines).hasSize(2);
        }
    }
}

