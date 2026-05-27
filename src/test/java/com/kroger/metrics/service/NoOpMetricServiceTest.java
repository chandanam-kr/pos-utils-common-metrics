package com.kroger.metrics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoOpMetricServiceTest
{
    private NoOpMetricService noOpMetricService;

    @BeforeEach
    void setUp()
    {
        noOpMetricService = new NoOpMetricService();
    }

    @Nested
    class InstanceTests
    {
        @Test
        void shouldBeInstanceOfMetricService()
        {
            assertThat(noOpMetricService).isInstanceOf(MetricService.class);
        }

        @Test
        void shouldBeInstanceOfNoOpMetricService()
        {
            assertThat(noOpMetricService).isInstanceOf(NoOpMetricService.class);
        }
    }

    @Nested
    class NoOpOperationsTests
    {
        @Test
        void countNameShouldDoNothing()
        {
            assertDoesNotThrow(() -> noOpMetricService.count("test.metric"));
        }

        @Test
        void countNameTagsShouldDoNothing()
        {
            assertDoesNotThrow(() -> noOpMetricService.count("test.metric", "key", "value"));
        }

        @Test
        void countIfTrueShouldDoNothing()
        {
            assertDoesNotThrow(() -> noOpMetricService.countIf(true, "test.metric", "key", "val"));
        }

        @Test
        void countIfFalseShouldDoNothing()
        {
            assertDoesNotThrow(() -> noOpMetricService.countIf(false, "test.metric"));
        }

        @Test
        void trackExceptionShouldDoNothing()
        {
            assertDoesNotThrow(() ->
                    noOpMetricService.trackException("test.failure", new RuntimeException("err")));
        }

        @Test
        void trackCriticalShouldDoNothing()
        {
            assertDoesNotThrow(() ->
                    noOpMetricService.trackCritical("test.critical", new RuntimeException("crit")));
        }

        @Test
        void trackErrorShouldDoNothing()
        {
            assertDoesNotThrow(() ->
                    noOpMetricService.trackError("test.error", "Something went wrong"));
        }

        @Test
        void recordTimeShouldDoNothing()
        {
            assertDoesNotThrow(() ->
                    noOpMetricService.recordTime("test.duration", 500L));
        }

        @Test
        void allOperationsWithNullArgsShouldNotThrow()
        {
            assertDoesNotThrow(() ->
            {
                noOpMetricService.count(null);
                noOpMetricService.count(null, (String[]) null);
                noOpMetricService.countIf(true, null);
                noOpMetricService.trackException(null, new RuntimeException());
                noOpMetricService.trackCritical(null, new RuntimeException());
                noOpMetricService.trackError(null, null);
                noOpMetricService.recordTime(null, 0L);
            });
        }
    }
}
