package com.kroger.metrics.service;

import com.kroger.metrics.service.MetricService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MetricService Tests")
class MetricServiceTest
{
    private MetricService metricService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp()
    {
        meterRegistry = new SimpleMeterRegistry();
        metricService = new MetricService(meterRegistry);
    }

    // ── Count Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("Count Tests")
    class CountTests
    {
        @Test
        @DisplayName("Should increment counter with no tags")
        void shouldIncrementCounterWithNoTags()
        {
            metricService.count("test.metric");

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment counter multiple times")
        void shouldIncrementCounterMultipleTimes()
        {
            metricService.count("test.metric");
            metricService.count("test.metric");
            metricService.count("test.metric");

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("Should increment counter with custom tags")
        void shouldIncrementCounterWithCustomTags()
        {
            metricService.count("test.metric", "storeId", "STORE-1");

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("storeId", "STORE-1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should handle multiple custom tags")
        void shouldHandleMultipleCustomTags()
        {
            metricService.count("test.metric",
                    "storeId", "STORE-1",
                    "team", "accounting",
                    "env", "prod");

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("storeId", "STORE-1")
                    .tag("team", "accounting")
                    .tag("env", "prod")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should add class and method tags automatically")
        void shouldAddClassAndMethodTagsAutomatically()
        {
            metricService.count("test.metric");

            // Just verify class and method tags exist (not specific values)
            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();

            boolean hasClassTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("class"));
            boolean hasMethodTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("method"));

            assertThat(hasClassTag).isTrue();
            assertThat(hasMethodTag).isTrue();
        }

        @Test
        @DisplayName("Should handle odd number of tag arguments gracefully")
        void shouldHandleOddNumberOfTags()
        {
            assertDoesNotThrow(() ->
                    metricService.count("test.metric", "key1", "value1", "orphan"));

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
        }
    }

    // ── CountIf Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("CountIf Tests")
    class CountIfTests
    {
        @Test
        @DisplayName("Should increment when condition is true")
        void shouldIncrementWhenConditionTrue()
        {
            metricService.countIf(true, "test.conditional");

            Counter counter = meterRegistry.find("test.conditional_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should not increment when condition is false")
        void shouldNotIncrementWhenConditionFalse()
        {
            metricService.countIf(false, "test.conditional");

            Counter counter = meterRegistry.find("test.conditional_total").counter();
            assertThat(counter).isNull();
        }

        @Test
        @DisplayName("Should pass tags when condition is true")
        void shouldPassTagsWhenConditionTrue()
        {
            metricService.countIf(true, "test.conditional",
                    "storeId", "STORE-1");

            Counter counter = meterRegistry.find("test.conditional_total")
                    .tag("storeId", "STORE-1")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    // ── TrackException Tests ───────────────────────────────────────

    @Nested
    @DisplayName("Track Exception Tests")
    class TrackExceptionTests
    {
        @Test
        @DisplayName("Should track exception with exception details")
        void shouldTrackExceptionWithDetails()
        {
            RuntimeException ex = new RuntimeException("Test error");

            metricService.trackException("test.failure", ex);

            Counter counter = meterRegistry.find("test.failure_total")
                    .tag("exception", "RuntimeException")
                    .tag("message", "Test error")
                    .tag("status", "failure")
                    .tag("critical", "false")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should handle null exception message")
        void shouldHandleNullExceptionMessage()
        {
            RuntimeException ex = new RuntimeException();

            metricService.trackException("test.failure", ex);

            Counter counter = meterRegistry.find("test.failure_total")
                    .tag("message", "no_message")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should track different exception types separately")
        void shouldTrackDifferentExceptionTypesSeparately()
        {
            metricService.trackException("test.failure", new RuntimeException("Runtime"));
            metricService.trackException("test.failure", new IllegalArgumentException("Illegal"));
            metricService.trackException("test.failure", new IOException("IO"));

            assertThat(meterRegistry.find("test.failure_total")
                    .tag("exception", "RuntimeException").counter()).isNotNull();
            assertThat(meterRegistry.find("test.failure_total")
                    .tag("exception", "IllegalArgumentException").counter()).isNotNull();
            assertThat(meterRegistry.find("test.failure_total")
                    .tag("exception", "IOException").counter()).isNotNull();
        }

        @Test
        @DisplayName("Should include custom tags with exception")
        void shouldIncludeCustomTagsWithException()
        {
            metricService.trackException("test.failure",
                    new RuntimeException("Error"),
                    "storeId", "STORE-1");

            Counter counter = meterRegistry.find("test.failure_total")
                    .tag("storeId", "STORE-1")
                    .tag("exception", "RuntimeException")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    // ── TrackCritical Tests ────────────────────────────────────────

    @Nested
    @DisplayName("Track Critical Tests")
    class TrackCriticalTests
    {
        @Test
        @DisplayName("Should track critical exception with critical flag")
        void shouldTrackCriticalWithFlag()
        {
            RuntimeException ex = new RuntimeException("Critical error");

            metricService.trackCritical("test.critical.failure", ex);

            Counter counter = meterRegistry.find("test.critical.failure_total")
                    .tag("critical", "true")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should include exception details in critical")
        void shouldIncludeExceptionDetailsInCritical()
        {
            IOException ex = new IOException("Connection failed");

            metricService.trackCritical("test.critical.failure", ex,
                    "service", "database");

            Counter counter = meterRegistry.find("test.critical.failure_total")
                    .tag("exception", "IOException")
                    .tag("message", "Connection failed")
                    .tag("status", "failure")
                    .tag("critical", "true")
                    .tag("service", "database")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    // ── TrackError Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Track Error Tests")
    class TrackErrorTests
    {
        @Test
        @DisplayName("Should track error with custom message")
        void shouldTrackErrorWithMessage()
        {
            metricService.trackError("test.error", "Something went wrong");

            Counter counter = meterRegistry.find("test.error_total")
                    .tag("message", "Something went wrong")
                    .tag("status", "error")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should handle null message gracefully")
        void shouldHandleNullMessage()
        {
            metricService.trackError("test.error", null);

            Counter counter = meterRegistry.find("test.error_total")
                    .tag("message", "no_message")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should include custom tags with error")
        void shouldIncludeCustomTagsWithError()
        {
            metricService.trackError("test.error", "Failed validation",
                    "field", "email");

            Counter counter = meterRegistry.find("test.error_total")
                    .tag("field", "email")
                    .tag("message", "Failed validation")
                    .tag("status", "error")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    // ── RecordTime Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Record Time Tests")
    class RecordTimeTests
    {
        @Test
        @DisplayName("Should record duration")
        void shouldRecordDuration()
        {
            metricService.recordTime("test.duration", 250);

            Timer timer = meterRegistry.find("test.duration_duration_seconds").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
        }

        @Test
        @DisplayName("Should record multiple durations")
        void shouldRecordMultipleDurations()
        {
            metricService.recordTime("test.duration", 100);
            metricService.recordTime("test.duration", 200);
            metricService.recordTime("test.duration", 300);

            Timer timer = meterRegistry.find("test.duration_duration_seconds").timer();
            assertThat(timer.count()).isEqualTo(3);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(600.0);
        }

        @Test
        @DisplayName("Should include custom tags with duration")
        void shouldIncludeCustomTagsWithDuration()
        {
            metricService.recordTime("test.duration", 150,
                    "operation", "query");

            Timer timer = meterRegistry.find("test.duration_duration_seconds")
                    .tag("operation", "query")
                    .timer();
            assertThat(timer).isNotNull();
        }

        @Test
        @DisplayName("Should handle zero duration")
        void shouldHandleZeroDuration()
        {
            metricService.recordTime("test.duration", 0);

            Timer timer = meterRegistry.find("test.duration_duration_seconds").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    // ── Safety Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Safety Tests")
    class SafetyTests
    {
        @Test
        @DisplayName("Should not break when meter registry fails")
        void shouldNotBreakWhenRegistryFails()
        {
            MeterRegistry brokenRegistry = mock(MeterRegistry.class);
            when(brokenRegistry.counter(any(String.class), any(Iterable.class)))
                    .thenThrow(new RuntimeException("Registry broken"));

            MetricService brokenService = new MetricService(brokenRegistry);

            assertDoesNotThrow(() -> brokenService.count("test.metric"));
            assertDoesNotThrow(() -> brokenService.count("test.metric", "key", "value"));
            assertDoesNotThrow(() -> brokenService.countIf(true, "test.metric"));
            assertDoesNotThrow(() -> brokenService.trackException("test.failure",
                    new RuntimeException()));
            assertDoesNotThrow(() -> brokenService.trackCritical("test.failure",
                    new RuntimeException()));
            assertDoesNotThrow(() -> brokenService.trackError("test.error", "msg"));
            assertDoesNotThrow(() -> brokenService.recordTime("test.duration", 100));
        }

        @Test
        @DisplayName("Should handle null metric name gracefully")
        void shouldHandleNullMetricName()
        {
            assertDoesNotThrow(() -> metricService.count(null));
        }

        @Test
        @DisplayName("Should handle null tags array")
        void shouldHandleNullTagsArray()
        {
            assertDoesNotThrow(() -> metricService.count("test.metric", (String[]) null));
        }

        @Test
        @DisplayName("Should handle empty tags array")
        void shouldHandleEmptyTagsArray()
        {
            metricService.count("test.metric", new String[]{});

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
        }
    }

    // ── Standard Tags Tests ────────────────────────────────────────

    @Nested
    @DisplayName("Standard Tags Tests")
    class StandardTagsTests
    {
        @Test
        @DisplayName("Should add class tag from caller")
        void shouldAddClassTagFromCaller()
        {
            metricService.count("test.metric");

            // Verify class tag exists (don't check specific value as it depends on stack)
            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();

            boolean hasClassTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("class"));
            assertThat(hasClassTag).isTrue();
        }

        @Test
        @DisplayName("Should add method tag from caller")
        void shouldAddMethodTagFromCaller()
        {
            metricService.count("test.metric");

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();

            boolean hasMethodTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("method"));
            assertThat(hasMethodTag).isTrue();
        }

        @Test
        @DisplayName("Should add standard tags to exception metrics")
        void shouldAddStandardTagsToExceptionMetrics()
        {
            metricService.trackException("test.failure", new RuntimeException("err"));

            Counter counter = meterRegistry.find("test.failure_total").counter();
            assertThat(counter).isNotNull();

            boolean hasClassTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("class"));
            boolean hasMethodTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("method"));

            assertThat(hasClassTag).isTrue();
            assertThat(hasMethodTag).isTrue();
        }

        @Test
        @DisplayName("Should add standard tags to error metrics")
        void shouldAddStandardTagsToErrorMetrics()
        {
            metricService.trackError("test.error", "msg");

            Counter counter = meterRegistry.find("test.error_total").counter();
            assertThat(counter).isNotNull();

            boolean hasClassTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("class"));
            assertThat(hasClassTag).isTrue();
        }

        @Test
        @DisplayName("Should add standard tags to timer metrics")
        void shouldAddStandardTagsToTimerMetrics()
        {
            metricService.recordTime("test.duration", 100);

            Timer timer = meterRegistry.find("test.duration_duration_seconds").timer();
            assertThat(timer).isNotNull();

            boolean hasClassTag = timer.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("class"));
            assertThat(hasClassTag).isTrue();
        }
    }

    // ── Integration Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests
    {
        @Test
        @DisplayName("Should handle complete workflow")
        void shouldHandleCompleteWorkflow()
        {
            metricService.count("api.request", "endpoint", "/orders");
            metricService.recordTime("api.duration", 150, "endpoint", "/orders");

            try
            {
                throw new RuntimeException("Order validation failed");
            }
            catch (RuntimeException ex)
            {
                metricService.trackException("api.failure", ex,
                        "endpoint", "/orders");
            }

            metricService.countIf(true, "api.suspicious",
                    "endpoint", "/orders");

            assertThat(meterRegistry.find("api.request_total").counter()).isNotNull();
            assertThat(meterRegistry.find("api.duration_duration_seconds").timer()).isNotNull();
            assertThat(meterRegistry.find("api.failure_total").counter()).isNotNull();
            assertThat(meterRegistry.find("api.suspicious_total").counter()).isNotNull();
        }

        @Test
        @DisplayName("Should track multiple metrics with same name and different tags")
        void shouldTrackMultipleMetricsWithDifferentTags()
        {
            metricService.count("orders.processed", "store", "store-1", "status", "success");
            metricService.count("orders.processed", "store", "store-1", "status", "failed");
            metricService.count("orders.processed", "store", "store-2", "status", "success");

            Counter store1Success = meterRegistry.find("orders.processed_total")
                    .tag("store", "store-1")
                    .tag("status", "success")
                    .counter();

            Counter store1Failed = meterRegistry.find("orders.processed_total")
                    .tag("store", "store-1")
                    .tag("status", "failed")
                    .counter();

            Counter store2Success = meterRegistry.find("orders.processed_total")
                    .tag("store", "store-2")
                    .tag("status", "success")
                    .counter();

            assertThat(store1Success).isNotNull();
            assertThat(store1Failed).isNotNull();
            assertThat(store2Success).isNotNull();
        }
    }
}