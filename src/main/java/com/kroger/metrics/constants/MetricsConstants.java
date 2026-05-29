package com.kroger.metrics.constants;

public final class MetricsConstants
{
    private MetricsConstants() {
        // Prevent instantiation
    }

    public static final String TAG_CLIENT_ERROR = "client_error";
    public static final String TAG_SERVER_ERROR = "server_error";
    public static final String TAG_UNKNOWN      = "unknown";

    // Log messages
    public static final String LOG_METRICS_FILTER_INIT       = "Metrics filter initialized — categories: {}, effective prefixes: {}, custom categories: {}";
    public static final String LOG_METRICS_UNFILTERED        = "Metrics filter: unfiltered mode enabled — all metrics will be exposed with type auto-derived from metric name";
    public static final String LOG_CATEGORY_NO_PREFIXES      = "Category '{}' has no prefixes defined. It will be ignored.";
    public static final String LOG_NO_PREFIXES_CONFIGURED    = "No metric prefixes configured. All metrics will be denied!";
    public static final String LOG_METER_FILTER_ERROR        = "MeterFilter error for [{}]: {}";
    public static final String LOG_COMMON_TAGS_INIT          = "Metrics common tags initialized — app: {}";
    public static final String LOG_APP_TAG_UNKNOWN           = "Could not resolve 'app' tag. Set 'spring.application.name' or 'metrics.app' in application.yml. Defaulting to 'unknown'.";
    public static final String METRIC_RECORDING_FAILED_LOG   = "Metric recording failed for [{}]: {}";
    public static final String LOG_CRITICAL_EXCEPTION        = "CRITICAL [{}]: {}";
    public static final String LOG_EXCEPTION                 = "Exception [{}]: {}";
    public static final String METRIC_TRACKING_FAILED        = "Exception metric tracking failed: {}";
    public static final String LOG_FAILED_RECORD_METRIC      = "Failed to record metric [{}]: {}";

    // Tag keys
    public static final String TAG_CLASS     = "class";
    public static final String TAG_METHOD    = "method";
    public static final String TAG_STATUS    = "status";
    public static final String TAG_EXCEPTION = "exception";
    public static final String TAG_MESSAGE   = "message";
    public static final String TAG_CRITICAL  = "critical";

    // Tag values
    public static final String STATUS_FAILURE = "failure";
    public static final String STATUS_ERROR   = "error";
    public static final String STATUS_SUCCESS = "success";
    public static final String UNRESOLVED     = "unresolved";
    public static final String NULL_VALUE     = "null";
    public static final String GET            = "get";
    public static final String NO_MESSAGE     = "no_message";

    // Metric suffixes
    public static final String SUFFIX_COUNTER = "_total";
    public static final String SUFFIX_TIMER   = "_duration_seconds";
    public static final String FAILURE_SUFFIX = "_failure";
    public static final String TOTAL_SUFFIX   = "_total";

    // Tag parsing symbols
    public static final String TAG_SEPARATOR        = "=";
    public static final String TAG_DYNAMIC_PREFIX   = "#";
    public static final String TAG_NESTED_SEPARATOR = ".";
}