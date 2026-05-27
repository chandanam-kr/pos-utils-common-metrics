package com.kroger.metrics.constants;

import java.util.List;
import java.util.Map;

public final class MetricsConstants
{
    private MetricsConstants() {
        // Prevent instantiation
    }

    public static final Map<String, String> METRIC_NAME_RENAMES = Map.ofEntries(
            Map.entry("http_server_requests_seconds_count",        "total_requests_completed"),
            Map.entry("http_server_requests_seconds_sum",          "total_requests_duration_seconds"),
            Map.entry("http_server_requests_seconds_max",          "peak_request_duration_seconds"),
            Map.entry("http_server_requests_active_seconds_count", "current_active_requests"),
            Map.entry("http_server_requests_active_seconds_sum",   "active_requests_duration_seconds"),
            Map.entry("http_server_requests_active_seconds_max",   "active_requests_peak_duration_seconds"),
            Map.entry("jvm_memory_used_bytes",                     "memory_currently_used_bytes"),
            Map.entry("jvm_memory_committed_bytes",                "memory_reserved_bytes"),
            Map.entry("jvm_memory_max_bytes",                      "memory_maximum_limit_bytes"),
            Map.entry("jvm_memory_usage_after_gc",                 "memory_used_after_garbage_collection"),
            Map.entry("jvm_threads_live_threads",                  "threads_currently_active"),
            Map.entry("jvm_threads_daemon_threads",                "threads_running_in_background"),
            Map.entry("jvm_threads_peak_threads",                  "threads_highest_count_ever"),
            Map.entry("jvm_threads_started_threads_total",         "threads_created_since_startup"),
            Map.entry("jvm_threads_states_threads",                "threads_grouped_by_state"),
            Map.entry("process_cpu_usage",                         "application_cpu_usage_percent"),
            Map.entry("process_cpu_time_ns_total",                 "application_cpu_time_nanoseconds"),
            Map.entry("system_cpu_usage",                          "system_cpu_usage_percent"),
            Map.entry("system_cpu_count",                          "system_available_cpu_cores"),
            Map.entry("logback_events_total",                      "log_entries_by_level")
    );

    public static final Map<String, String> METRIC_TYPE = Map.ofEntries(
            Map.entry("http_server_requests_seconds_count",        "http"),
            Map.entry("http_server_requests_seconds_sum",          "http"),
            Map.entry("http_server_requests_seconds_max",          "http"),
            Map.entry("http_server_requests_active_seconds_count", "http"),
            Map.entry("http_server_requests_active_seconds_sum",   "http"),
            Map.entry("http_server_requests_active_seconds_max",   "http"),
            Map.entry("jvm_memory_used_bytes",                     "jvm"),
            Map.entry("jvm_memory_committed_bytes",                "jvm"),
            Map.entry("jvm_memory_max_bytes",                      "jvm"),
            Map.entry("jvm_memory_usage_after_gc",                 "jvm"),
            Map.entry("jvm_threads_live_threads",                  "jvm"),
            Map.entry("jvm_threads_daemon_threads",                "jvm"),
            Map.entry("jvm_threads_peak_threads",                  "jvm"),
            Map.entry("jvm_threads_started_threads_total",         "jvm"),
            Map.entry("jvm_threads_states_threads",                "jvm"),
            Map.entry("process_cpu_usage",                         "process"),
            Map.entry("process_cpu_time_ns_total",                 "process"),
            Map.entry("system_cpu_usage",                          "system"),
            Map.entry("system_cpu_count",                          "system"),
            Map.entry("logback_events_total",                      "logging")
    );

    public static final Map<String, String> TAG_VALUE_RENAMES = Map.ofEntries(
            Map.entry("CLIENT_ERROR",           "client_error_4xx"),
            Map.entry("SUCCESS",                "success_2xx"),
            Map.entry("SERVER_ERROR",           "server_error_5xx"),
            Map.entry("REDIRECTION",            "redirection_3xx"),
            Map.entry("G1 Old Gen",             "heap_old_generation"),
            Map.entry("G1 Eden Space",          "heap_eden_space"),
            Map.entry("G1 Survivor Space",      "heap_survivor_space"),
            Map.entry("CodeCache",              "nonheap_code_cache"),
            Map.entry("Metaspace",              "nonheap_metaspace"),
            Map.entry("Compressed Class Space", "nonheap_class_space"),
            Map.entry("runnable",               "actively_running"),
            Map.entry("waiting",                "waiting_indefinitely"),
            Map.entry("timed-waiting",          "waiting_with_timeout"),
            Map.entry("blocked",                "blocked_on_lock"),
            Map.entry("new",                    "not_yet_started"),
            Map.entry("terminated",             "finished_execution")
    );

    public static final List<String> DEFAULT_ALLOWED = List.of(
            "jvm.memory",
            "jvm.threads",
            "http.server",
            "process.cpu",
            "system.cpu",
            "logback"
    );

    // Log messages
    public static final String LOG_METRICS_FILTER_INIT       = "Metrics filter initialized with defaults {} and additional prefixes {}";
    public static final String LOG_METER_FILTER_ERROR        = "MeterFilter error for [{}]: {}";
    public static final String LOG_METRICS_CONTROLLER_ERROR  = "Failed to generate metrics: {}";
    public static final String ERROR_GENERATING_METRICS_BODY = "# Error generating metrics\n";
    public static final String METRIC_RECORDING_FAILED_LOG   = "Metric recording failed for [{}]: {}";
    public static final String LOG_CRITICAL_EXCEPTION        = "CRITICAL [{}]: {}";
    public static final String LOG_EXCEPTION                 = "Exception [{}]: {}";
    public static final String METRIC_TRACKING_FAILED        = "Exception metric tracking failed: {}";

    // Tag keys
    public static final String TAG_CLASS        = "class";
    public static final String TAG_METHOD       = "method";
    public static final String TAG_STATUS       = "status";
    // Tag values
    public static final String STATUS_FAILURE   = "failure";
    public static final String STATUS_ERROR     = "error";
    public static final String UNRESOLVED       = "unresolved";
    public static final String NULL_VALUE       = "null";
    public static final String GET              = "get";
    public static final String TAG_EXCEPTION    = "exception";
    public static final String TAG_MESSAGE      = "message";
    public static final String TAG_CRITICAL     = "critical";
    // Metric suffixes
    public static final String SUFFIX_COUNTER   = "_total";
    public static final String SUFFIX_TIMER     = "_duration_seconds";
    public static final String FAILURE_SUFFIX   = "_failure";
    public static final String TOTAL_SUFFIX     = "_total";
    public static final String NO_MESSAGE       = "no_message";
    // Tag parsing symbols
    public static final String TAG_SEPARATOR        = "=";
    public static final String TAG_DYNAMIC_PREFIX   = "#";
    public static final String TAG_NESTED_SEPARATOR = ".";
    public static final String LOG_FAILED_RECORD_METRIC = "Failed to record metric [{}]: {}";
}