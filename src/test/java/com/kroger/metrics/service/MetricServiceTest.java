package com.kroger.metrics.service;

import com.kroger.metrics.service.MetricService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
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

    @Nested
    class CountTests
    {
        @Test
        void shouldIncrementCounterWithNoTags()
        {
            metricService.count("test.metric");

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementCounterMultipleTimes()
        {
            metricService.count("test.metric");
            metricService.count("test.metric");
            metricService.count("test.metric");

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
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
        void shouldHandleOddNumberOfTags()
        {
            assertDoesNotThrow(() ->
                    metricService.count("test.metric", "key1", "value1", "orphan"));

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
        }
    }

    @Nested
    class CountIfTests
    {
        @Test
        void shouldIncrementWhenConditionTrue()
        {
            metricService.countIf(true, "test.conditional");

            Counter counter = meterRegistry.find("test.conditional_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldNotIncrementWhenConditionFalse()
        {
            metricService.countIf(false, "test.conditional");

            Counter counter = meterRegistry.find("test.conditional_total").counter();
            assertThat(counter).isNull();
        }

        @Test
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

    @Nested
    class TrackExceptionTests
    {
        @Test
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

    @Nested
    class TrackCriticalTests
    {
        @Test
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

    @Nested
    class TrackErrorTests
    {
        @Test
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
        void shouldHandleNullMessage()
        {
            metricService.trackError("test.error", null);

            Counter counter = meterRegistry.find("test.error_total")
                    .tag("message", "no_message")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
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

    @Nested
    class RecordTimeTests
    {
        @Test
        void shouldRecordDuration()
        {
            metricService.recordTime("test.duration", 250);

            Timer timer = meterRegistry.find("test.duration_duration_seconds").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
        }

        @Test
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
        void shouldHandleZeroDuration()
        {
            metricService.recordTime("test.duration", 0);

            Timer timer = meterRegistry.find("test.duration_duration_seconds").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    class SafetyTests
    {
        @Test
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
        void shouldHandleNullMetricName()
        {
            assertDoesNotThrow(() -> metricService.count(null));
        }

        @Test
        void shouldHandleNullTagsArray()
        {
            assertDoesNotThrow(() -> metricService.count("test.metric", (String[]) null));
        }

        @Test
        void shouldHandleEmptyTagsArray()
        {
            metricService.count("test.metric", new String[]{});

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
        }
    }

    @Nested
    class StandardTagsTests
    {
        @Test
        void shouldAddClassTagFromCaller()
        {
            metricService.count("test.metric");

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();

            boolean hasClassTag = counter.getId().getTags().stream()
                    .anyMatch(tag -> tag.getKey().equals("class"));
            assertThat(hasClassTag).isTrue();
        }

        @Test
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

    @Nested
    class IntegrationTests
    {
        @Test
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