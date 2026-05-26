package com.kroger.metrics.constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsConstants
{
    public static final Map<String, String> METRIC_NAME_RENAMES = new HashMap<>()
    {{
        put("http_server_requests_seconds_count",        "total_requests_completed");
        put("http_server_requests_seconds_sum",          "total_requests_duration_seconds");
        put("http_server_requests_seconds_max",          "peak_request_duration_seconds");
        put("http_server_requests_active_seconds_count", "current_active_requests");
        put("http_server_requests_active_seconds_sum",   "active_requests_duration_seconds");
        put("http_server_requests_active_seconds_max",   "active_requests_peak_duration_seconds");
        put("jvm_memory_used_bytes",                     "memory_currently_used_bytes");
        put("jvm_memory_committed_bytes",                "memory_reserved_bytes");
        put("jvm_memory_max_bytes",                      "memory_maximum_limit_bytes");
        put("jvm_memory_usage_after_gc",                 "memory_used_after_garbage_collection");
        put("jvm_threads_live_threads",                  "threads_currently_active");
        put("jvm_threads_daemon_threads",                "threads_running_in_background");
        put("jvm_threads_peak_threads",                  "threads_highest_count_ever");
        put("jvm_threads_started_threads_total",         "threads_created_since_startup");
        put("jvm_threads_states_threads",                "threads_grouped_by_state");
        put("process_cpu_usage",                         "application_cpu_usage_percent");
        put("process_cpu_time_ns_total",                 "application_cpu_time_nanoseconds");
        put("system_cpu_usage",                          "system_cpu_usage_percent");
        put("system_cpu_count",                          "system_available_cpu_cores");
        put("logback_events_total",                      "log_entries_by_level");
    }};

    public static final Map<String, String> METRIC_TYPE = new HashMap<>()
    {{
        put("http_server_requests_seconds_count",        "http");
        put("http_server_requests_seconds_sum",          "http");
        put("http_server_requests_seconds_max",          "http");
        put("http_server_requests_active_seconds_count", "http");
        put("http_server_requests_active_seconds_sum",   "http");
        put("http_server_requests_active_seconds_max",   "http");
        put("jvm_memory_used_bytes",                     "jvm");
        put("jvm_memory_committed_bytes",                "jvm");
        put("jvm_memory_max_bytes",                      "jvm");
        put("jvm_memory_usage_after_gc",                 "jvm");
        put("jvm_threads_live_threads",                  "jvm");
        put("jvm_threads_daemon_threads",                "jvm");
        put("jvm_threads_peak_threads",                  "jvm");
        put("jvm_threads_started_threads_total",         "jvm");
        put("jvm_threads_states_threads",                "jvm");
        put("process_cpu_usage",                         "process");
        put("process_cpu_time_ns_total",                 "process");
        put("system_cpu_usage",                          "system");
        put("system_cpu_count",                          "system");
        put("logback_events_total",                      "logging");
    }};

    public static final Map<String, String> TAG_VALUE_RENAMES = new HashMap<>()
    {{
        put("CLIENT_ERROR",           "client_error_4xx");
        put("SUCCESS",                "success_2xx");
        put("SERVER_ERROR",           "server_error_5xx");
        put("REDIRECTION",            "redirection_3xx");
        put("G1 Old Gen",             "heap_old_generation");
        put("G1 Eden Space",          "heap_eden_space");
        put("G1 Survivor Space",      "heap_survivor_space");
        put("CodeCache",              "nonheap_code_cache");
        put("Metaspace",              "nonheap_metaspace");
        put("Compressed Class Space", "nonheap_class_space");
        put("runnable",               "actively_running");
        put("waiting",                "waiting_indefinitely");
        put("timed-waiting",          "waiting_with_timeout");
        put("blocked",                "blocked_on_lock");
        put("new",                    "not_yet_started");
        put("terminated",             "finished_execution");
    }};
    
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
    public static final String STATUS_SUCCESS   = "success";
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