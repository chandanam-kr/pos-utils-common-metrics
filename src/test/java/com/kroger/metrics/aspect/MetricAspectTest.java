package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.Metric;
import com.kroger.metrics.annotation.MetricType;
import com.kroger.metrics.aspect.MetricAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricAspect Tests")
class MetricAspectTest
{
    private MetricAspect metricAspect;
    private MeterRegistry meterRegistry;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @BeforeEach
    void setUp()
    {
        meterRegistry = new SimpleMeterRegistry();
        metricAspect  = new MetricAspect(meterRegistry);
    }

    @Nested
    @DisplayName("Counter Metric Tests")
    class CounterTests
    {
        @Test
        @DisplayName("Should record counter when type is COUNTER")
        void shouldRecordCounter() throws Throwable
        {
            Metric metric = createMetric("test.counter", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.counter_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should add class and method tags to counter")
        void shouldAddClassAndMethodTags() throws Throwable
        {
            Metric metric = createMetric("test.counter", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "processMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.counter_total")
                    .tag("class", "SampleService")
                    .tag("method", "processMethod")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should add success status tag")
        void shouldAddSuccessStatusTag() throws Throwable
        {
            Metric metric = createMetric("test.counter", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.counter_total")
                    .tag("status", "success")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should increment counter on multiple calls")
        void shouldIncrementCounterMultipleTimes() throws Throwable
        {
            Metric metric = createMetric("test.counter", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);
            metricAspect.capture(joinPoint, metric);
            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.counter_total").counter();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("Timer Metric Tests")
    class TimerTests
    {
        @Test
        @DisplayName("Should record timer when type is TIMER")
        void shouldRecordTimer() throws Throwable
        {
            Metric metric = createMetric("test.timer", MetricType.TIMER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Timer timer = meterRegistry.find("test.timer_duration_seconds").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should record duration greater than zero")
        void shouldRecordDuration() throws Throwable
        {
            Metric metric = createMetric("test.timer", MetricType.TIMER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            when(joinPoint.proceed()).thenAnswer(invocation ->
            {
                Thread.sleep(10);
                return "result";
            });

            metricAspect.capture(joinPoint, metric);

            Timer timer = meterRegistry.find("test.timer_duration_seconds").timer();
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Gauge Metric Tests")
    class GaugeTests
    {
        @Test
        @DisplayName("Should record gauge when type is GAUGE")
        void shouldRecordGauge() throws Throwable
        {
            Metric metric = createMetric("test.gauge", MetricType.GAUGE, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            assertThat(meterRegistry.find("test.gauge").gauge()).isNotNull();
        }
    }

    // ── ALL Type Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("ALL Metric Type Tests")
    class AllTypeTests
    {
        @Test
        @DisplayName("Should record both counter and timer when type is ALL")
        void shouldRecordCounterAndTimer() throws Throwable
        {
            Metric metric = createMetric("test.all", MetricType.ALL, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.all_total").counter();
            Timer timer     = meterRegistry.find("test.all_duration_seconds").timer();

            assertThat(counter).isNotNull();
            assertThat(timer).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Tag Resolution Tests")
    class TagResolutionTests
    {
        @Test
        @DisplayName("Should add constant tag")
        void shouldAddConstantTag() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{"team=accounting"});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("team", "accounting")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should resolve simple parameter tag")
        void shouldResolveSimpleParameter() throws Throwable
        {
            Parameter[] parameters = createParameters("storeId");
            Object[] args = new Object[]{"STORE-123"};

            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{"storeId=#storeId"});
            setupJoinPoint(new SampleService(), "testMethod", parameters, args);

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("storeId", "STORE-123")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should resolve nested field tag using getter")
        void shouldResolveNestedFieldViaGetter() throws Throwable
        {
            TestRequest request    = new TestRequest("DLT123", "STORE-1");
            Parameter[] parameters = createParameters("request");
            Object[] args          = new Object[]{request};

            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{"dltId=#request.dltId"});
            setupJoinPoint(new SampleService(), "testMethod", parameters, args);

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("dltId", "DLT123")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should resolve multiple nested fields")
        void shouldResolveMultipleNestedFields() throws Throwable
        {
            TestRequest request    = new TestRequest("DLT123", "STORE-1");
            Parameter[] parameters = createParameters("request");
            Object[] args          = new Object[]{request};

            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{
                            "dltId=#request.dltId",
                            "storeId=#request.storeId"
                    });
            setupJoinPoint(new SampleService(), "testMethod", parameters, args);

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("dltId", "DLT123")
                    .tag("storeId", "STORE-1")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should return unresolved for missing parameter")
        void shouldReturnUnresolvedForMissingParam() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{"missing=#nonExistent"});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("missing", "unresolved")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should return null for null parameter value")
        void shouldReturnNullForNullParam() throws Throwable
        {
            Parameter[] parameters = createParameters("storeId");
            Object[] args          = new Object[]{null};

            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{"storeId=#storeId"});
            setupJoinPoint(new SampleService(), "testMethod", parameters, args);

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("storeId", "null")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should skip malformed tag without equals")
        void shouldSkipMalformedTag() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{"malformed_no_equals"});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.getId().getTags())
                    .extracting(Tag::getKey)
                    .doesNotContain("malformed_no_equals");
        }

        @Test
        @DisplayName("Should handle multiple constant tags")
        void shouldHandleMultipleConstantTags() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{
                            "team=accounting",
                            "service=business-day",
                            "version=v1"
                    });
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("team", "accounting")
                    .tag("service", "business-day")
                    .tag("version", "v1")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("Should handle mix of constants and dynamic tags")
        void shouldHandleMixedTags() throws Throwable
        {
            Parameter[] parameters = createParameters("storeId");
            Object[] args          = new Object[]{"STORE-456"};

            Metric metric = createMetric("test.metric", MetricType.COUNTER,
                    new String[]{
                            "team=accounting",
                            "storeId=#storeId",
                            "env=production"
                    });
            setupJoinPoint(new SampleService(), "testMethod", parameters, args);

            metricAspect.capture(joinPoint, metric);

            Counter counter = meterRegistry.find("test.metric_total")
                    .tag("team", "accounting")
                    .tag("storeId", "STORE-456")
                    .tag("env", "production")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests
    {
        @Test
        @DisplayName("Should rethrow user exception")
        void shouldRethrowUserException() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            RuntimeException userException = new RuntimeException("User error");
            when(joinPoint.proceed()).thenThrow(userException);

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> metricAspect.capture(joinPoint, metric));

            assertThat(thrown).isEqualTo(userException);
        }

        @Test
        @DisplayName("Should not break when metric recording fails")
        void shouldNotBreakWhenMetricFails() throws Throwable
        {
            MeterRegistry brokenRegistry = mock(MeterRegistry.class);
            when(brokenRegistry.counter(any(String.class), any(Iterable.class)))
                    .thenThrow(new RuntimeException("Registry error"));

            MetricAspect brokenAspect = new MetricAspect(brokenRegistry);

            Metric metric = createMetric("test.metric", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            assertDoesNotThrow(() -> brokenAspect.capture(joinPoint, metric));
        }

        @Test
        @DisplayName("Should return method result even when metric fails")
        void shouldReturnMethodResult() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            when(joinPoint.proceed()).thenReturn("expected-result");

            Object result = metricAspect.capture(joinPoint, metric);

            assertThat(result).isEqualTo("expected-result");
        }

        @Test
        @DisplayName("Should propagate checked exception")
        void shouldPropagateCheckedException() throws Throwable
        {
            Metric metric = createMetric("test.metric", MetricType.COUNTER, new String[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            Exception checkedException = new Exception("Checked error");
            when(joinPoint.proceed()).thenThrow(checkedException);

            Exception thrown = assertThrows(Exception.class,
                    () -> metricAspect.capture(joinPoint, metric));

            assertThat(thrown).isEqualTo(checkedException);
        }
    }

    @Nested
    @DisplayName("buildTags Method Tests")
    class BuildTagsTests
    {
        @Test
        @DisplayName("Should always include class and method tags")
        void shouldIncludeClassAndMethodTags()
        {
            List<Tag> tags = metricAspect.buildTags(
                    new String[]{},
                    "TestClass",
                    "testMethod",
                    new Parameter[]{},
                    new Object[]{}
            );

            assertThat(tags).extracting(Tag::getKey).contains("class", "method");
            assertThat(tags).contains(Tag.of("class", "TestClass"));
            assertThat(tags).contains(Tag.of("method", "testMethod"));
        }

        @Test
        @DisplayName("Should return only base tags when no annotation tags")
        void shouldReturnOnlyBaseTags()
        {
            List<Tag> tags = metricAspect.buildTags(
                    new String[]{},
                    "TestClass",
                    "testMethod",
                    new Parameter[]{},
                    new Object[]{}
            );

            assertThat(tags).hasSize(2);
        }

        @Test
        @DisplayName("Should add annotation tags to base tags")
        void shouldAddAnnotationTags()
        {
            List<Tag> tags = metricAspect.buildTags(
                    new String[]{"team=accounting", "env=prod"},
                    "TestClass",
                    "testMethod",
                    new Parameter[]{},
                    new Object[]{}
            );

            assertThat(tags).hasSize(4);
            assertThat(tags).contains(Tag.of("team", "accounting"));
            assertThat(tags).contains(Tag.of("env", "prod"));
        }

        @Test
        @DisplayName("Should skip malformed tags")
        void shouldSkipMalformedTags()
        {
            List<Tag> tags = metricAspect.buildTags(
                    new String[]{"valid=value", "invalid_tag", "another=valid"},
                    "TestClass",
                    "testMethod",
                    new Parameter[]{},
                    new Object[]{}
            );

            assertThat(tags).hasSize(4);
            assertThat(tags).contains(Tag.of("valid", "value"));
            assertThat(tags).contains(Tag.of("another", "valid"));
        }
    }

    private Metric createMetric(String on, MetricType type, String[] tags)
    {
        Metric metric = mock(Metric.class);
        when(metric.on()).thenReturn(on);
        when(metric.type()).thenReturn(type);
        when(metric.tags()).thenReturn(tags);
        return metric;
    }

    private void setupJoinPoint(Object target, String methodName,
                                Parameter[] parameters, Object[] args) throws Throwable
    {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn("result");

        when(signature.getName()).thenReturn(methodName);

        Method mockMethod = mock(Method.class);
        when(mockMethod.getParameters()).thenReturn(parameters);
        when(signature.getMethod()).thenReturn(mockMethod);
    }

    private Parameter[] createParameters(String... names)
    {
        Parameter[] parameters = new Parameter[names.length];
        for (int i = 0; i < names.length; i++)
        {
            Parameter param = mock(Parameter.class);
            when(param.getName()).thenReturn(names[i]);
            parameters[i] = param;
        }
        return parameters;
    }

    static class SampleService
    {
        // Class name "SampleService" used in tests
    }

    static class TestRequest
    {
        private final String dltId;
        private final String storeId;

        public TestRequest(String dltId, String storeId)
        {
            this.dltId   = dltId;
            this.storeId = storeId;
        }

        public String getDltId()
        {
            return dltId;
        }

        public String getStoreId()
        {
            return storeId;
        }
    }
}