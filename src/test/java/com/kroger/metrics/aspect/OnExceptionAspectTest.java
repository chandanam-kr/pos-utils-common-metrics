package com.kroger.metrics.aspect;

import com.kroger.metrics.annotation.OnException;
import com.kroger.metrics.annotation.Track;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.xml.bind.ValidationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.zip.DataFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OnExceptionAspectTest
{
    private OnExceptionAspect onExceptionAspect;
    private MetricAspect metricAspect;
    private MeterRegistry meterRegistry;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @BeforeEach
    void setUp()
    {
        meterRegistry     = new SimpleMeterRegistry();
        metricAspect      = new MetricAspect(meterRegistry);
        onExceptionAspect = new OnExceptionAspect(meterRegistry, metricAspect);
    }

    @Nested
    class SuccessPathTests
    {
        @Test
        void shouldNotRecordOnSuccess() throws Throwable
        {
            OnException onException = createOnException("test.event", new String[]{},
                    new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenReturn("success");

            Object result = onExceptionAspect.capture(joinPoint, onException);

            assertThat(result).isEqualTo("success");
            assertThat(meterRegistry.getMeters()).isEmpty();
        }

        @Test
        void shouldReturnMethodResult() throws Throwable
        {
            OnException onException = createOnException("test.event", new String[]{},
                    new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenReturn("expected-result");

            Object result = onExceptionAspect.capture(joinPoint, onException);

            assertThat(result).isEqualTo("expected-result");
        }
    }

    @Nested
    class DefaultFailureTests
    {
        @Test
        void shouldRecordDefaultFailure() throws Throwable
        {
            OnException onException = createOnException("business.day.get.active",
                    new String[]{}, new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Some error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.get.active_failure_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldNotRecordWhenOnIsEmpty() throws Throwable
        {
            OnException onException = createOnException("", new String[]{},
                    new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Some error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.getMeters()).isEmpty();
        }

        @Test
        void shouldAddExceptionTag() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error message"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("exception", "RuntimeException")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldAddMessageTag() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Custom error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("message", "Custom error")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldHandleNullMessage() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException());

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("message", "no_message")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldSetCriticalFalseForDefault() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("critical", "false")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldSetStatusFailureTag() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});
            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("status", "failure")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    @Nested
    class TrackMatchTests
    {
        @Test
        void shouldRecordTrackedException() throws Throwable
        {
            Track validationTrack = createTrack(ValidationException.class,
                    "business.day.validation.failure", false);

            OnException onException = createOnException("", new String[]{},
                    new Class[]{}, new Track[]{validationTrack});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new ValidationException("Invalid input"));

            assertThrows(ValidationException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.validation.failure_total").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldRecordCriticalTracked() throws Throwable
        {
            Track dbTrack = createTrack(DataFormatException.class,
                    "business.day.db.failure", true);

            OnException onException = createOnException("", new String[]{},
                    new Class[]{}, new Track[]{dbTrack});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new DataFormatException("DB Error") {});

            assertThrows(DataFormatException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.db.failure_total")
                    .tag("critical", "true")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldMatchFirstTrack() throws Throwable
        {
            Track track1 = createTrack(ValidationException.class, "first.metric", false);
            Track track2 = createTrack(RuntimeException.class, "second.metric", false);

            OnException onException = createOnException("", new String[]{},
                    new Class[]{}, new Track[]{track1, track2});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new ValidationException("Error"));

            assertThrows(ValidationException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.find("first.metric_total").counter()).isNotNull();
            assertThat(meterRegistry.find("second.metric_total").counter()).isNull();
        }

        @Test
        void shouldNotRecordWhenNoTrackMatch() throws Throwable
        {
            Track track = createTrack(ValidationException.class, "validation.failure", false);

            OnException onException = createOnException("", new String[]{},
                    new Class[]{}, new Track[]{track});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Different exception"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.find("validation.failure_total").counter()).isNull();
        }

        @Test
        void shouldMatchSubclass() throws Throwable
        {
            Track track = createTrack(RuntimeException.class, "runtime.failure", false);

            OnException onException = createOnException("", new String[]{},
                    new Class[]{}, new Track[]{track});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("Bad arg"));

            assertThrows(IllegalArgumentException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("runtime.failure_total").counter();
            assertThat(counter).isNotNull();
        }
    }

    @Nested
    class TrackWithDefaultTests
    {
        @Test
        void shouldUseTrackWhenMatches() throws Throwable
        {
            Track validationTrack = createTrack(ValidationException.class,
                    "business.day.validation.failure", false);

            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{validationTrack});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new ValidationException("Invalid"));

            assertThrows(ValidationException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.find("business.day.validation.failure_total").counter()).isNotNull();
            assertThat(meterRegistry.find("business.day.api_failure_total").counter()).isNull();
        }

        @Test
        void shouldUseDefaultWhenNoMatch() throws Throwable
        {
            Track validationTrack = createTrack(ValidationException.class,
                    "business.day.validation.failure", false);

            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{validationTrack});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Other error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.find("business.day.api_failure_total").counter()).isNotNull();
            assertThat(meterRegistry.find("business.day.validation.failure_total").counter()).isNull();
        }
    }

    @Nested
    class IgnoreListTests
    {
        @Test
        void shouldNotRecordIgnoredException() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{},
                    new Class[]{IllegalArgumentException.class},
                    new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("Ignored"));

            assertThrows(IllegalArgumentException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.getMeters()).isEmpty();
        }

        @Test
        void shouldRecordWhenNotIgnored() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{},
                    new Class[]{IllegalArgumentException.class},
                    new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Not ignored"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.find("business.day.api_failure_total").counter()).isNotNull();
        }

        @Test
        void shouldHandleMultipleIgnored() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{},
                    new Class[]{
                            IllegalArgumentException.class,
                            NullPointerException.class
                    },
                    new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new NullPointerException("Ignored NPE"));

            assertThrows(NullPointerException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.getMeters()).isEmpty();
        }

        @Test
        void shouldIgnoreSubclass() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{},
                    new Class[]{RuntimeException.class},
                    new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("Subclass"));

            assertThrows(IllegalArgumentException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(meterRegistry.getMeters()).isEmpty();
        }
    }

    @Nested
    class TagsTests
    {
        @Test
        void shouldAddClassAndMethodTags() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "processMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("class", "SampleService")
                    .tag("method", "processMethod")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldAddCustomConstantTags() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{"team=accounting"},
                    new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("team", "accounting")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldResolveDynamicTags() throws Throwable
        {
            Parameter[] parameters = createParameters("storeId");
            Object[] args = new Object[]{"STORE-123"};

            OnException onException = createOnException("business.day.api",
                    new String[]{"storeId=#storeId"},
                    new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", parameters, args);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("storeId", "STORE-123")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        void shouldResolveNestedFields() throws Throwable
        {
            TestRequest request = new TestRequest("DLT123", "STORE-1");
            Parameter[] parameters = createParameters("request");
            Object[] args = new Object[]{request};

            OnException onException = createOnException("business.day.api",
                    new String[]{"dltId=#request.dltId"},
                    new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", parameters, args);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

            assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            Counter counter = meterRegistry.find("business.day.api_failure_total")
                    .tag("dltId", "DLT123")
                    .counter();
            assertThat(counter).isNotNull();
        }
    }

    @Nested
    class ExceptionPropagationTests
    {
        @Test
        void shouldAlwaysRethrowException() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            RuntimeException userException = new RuntimeException("User error");
            when(joinPoint.proceed()).thenThrow(userException);

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(thrown).isEqualTo(userException);
        }

        @Test
        void shouldRethrowIgnoredException() throws Throwable
        {
            OnException onException = createOnException("business.day.api",
                    new String[]{},
                    new Class[]{IllegalArgumentException.class},
                    new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            IllegalArgumentException ignored = new IllegalArgumentException("Ignored");
            when(joinPoint.proceed()).thenThrow(ignored);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> onExceptionAspect.capture(joinPoint, onException));

            assertThat(thrown).isEqualTo(ignored);
        }

        @Test
        void shouldRethrowWhenMetricFails() throws Throwable
        {
            MeterRegistry brokenRegistry = mock(MeterRegistry.class);
            when(brokenRegistry.counter(any(String.class), any(Iterable.class)))
                    .thenThrow(new RuntimeException("Registry broken"));

            OnExceptionAspect brokenAspect = new OnExceptionAspect(brokenRegistry, metricAspect);

            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});

            RuntimeException userException = new RuntimeException("User error");
            when(joinPoint.proceed()).thenThrow(userException);

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> brokenAspect.capture(joinPoint, onException));

            assertThat(thrown).isEqualTo(userException);
        }

        @Test
        void shouldNotBreakOnMetricFailure() throws Throwable
        {
            MeterRegistry brokenRegistry = mock(MeterRegistry.class);
            when(brokenRegistry.counter(any(String.class), any(Iterable.class)))
                    .thenThrow(new RuntimeException("Registry broken"));

            OnExceptionAspect brokenAspect = new OnExceptionAspect(brokenRegistry, metricAspect);

            OnException onException = createOnException("business.day.api",
                    new String[]{}, new Class[]{}, new Track[]{});

            setupJoinPoint(new SampleService(), "testMethod", new Parameter[]{}, new Object[]{});
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Original"));

            assertDoesNotThrow(() -> {
                try {
                    brokenAspect.capture(joinPoint, onException);
                } catch (RuntimeException e) {
                    // Expected user exception
                }
            });
        }
    }

    private OnException createOnException(String on, String[] tags,
                                          Class<? extends Throwable>[] ignore,
                                          Track[] track)
    {
        OnException onException = mock(OnException.class);
        lenient().when(onException.on()).thenReturn(on);
        lenient().when(onException.tags()).thenReturn(tags);
        lenient().when(onException.ignore()).thenReturn(ignore);
        lenient().when(onException.track()).thenReturn(track);
        return onException;
    }

    private Track createTrack(Class<? extends Throwable> type, String metric, boolean critical)
    {
        Track track = mock(Track.class);
        lenient().when(track.type()).thenReturn((Class) type);
        lenient().when(track.metric()).thenReturn(metric);
        lenient().when(track.critical()).thenReturn(critical);
        return track;
    }

    private void setupJoinPoint(Object target, String methodName,
                                Parameter[] parameters, Object[] args) throws Throwable
    {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(joinPoint.getArgs()).thenReturn(args);
        lenient().when(joinPoint.getTarget()).thenReturn(target);
        lenient().when(joinPoint.proceed()).thenReturn("result");

        lenient().when(signature.getName()).thenReturn(methodName);

        Method mockMethod = mock(Method.class);
        lenient().when(mockMethod.getParameters()).thenReturn(parameters);
        lenient().when(signature.getMethod()).thenReturn(mockMethod);
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