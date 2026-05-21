package com.kroger.metrics.constants;

import java.util.HashMap;
import java.util.Map;

public class MetricsConstants {

    public static final Map<String, String> METRIC_NAME_RENAMES = new HashMap<>() {{
        // HTTP
        put("http_server_requests_seconds_count",           "total_requests_completed");
        put("http_server_requests_seconds_sum",             "total_requests_duration_seconds");
        put("http_server_requests_seconds_max",             "peak_request_duration_seconds");
        put("http_server_requests_active_seconds_count",    "current_active_requests");
        put("http_server_requests_active_seconds_sum",      "active_requests_duration_seconds");
        put("http_server_requests_active_seconds_max",      "active_requests_peak_duration_seconds");
        // JVM Memory
        put("jvm_memory_used_bytes",                        "memory_currently_used_bytes");
        put("jvm_memory_committed_bytes",                   "memory_reserved_bytes");
        put("jvm_memory_max_bytes",                         "memory_maximum_limit_bytes");
        put("jvm_memory_usage_after_gc",                    "memory_used_after_garbage_collection");
        // JVM Threads
        put("jvm_threads_live_threads",                     "threads_currently_active");
        put("jvm_threads_daemon_threads",                   "threads_running_in_background");
        put("jvm_threads_peak_threads",                     "threads_highest_count_ever");
        put("jvm_threads_started_threads_total",            "threads_created_since_startup");
        put("jvm_threads_states_threads",                   "threads_grouped_by_state");
        // CPU
        put("process_cpu_usage",                            "application_cpu_usage_percent");
        put("process_cpu_time_ns_total",                    "application_cpu_time_nanoseconds");
        put("system_cpu_usage",                             "system_cpu_usage_percent");
        put("system_cpu_count",                             "system_available_cpu_cores");
        // Logs
        put("logback_events_total",                         "log_entries_by_level");
    }};

    public static final Map<String, String> TAG_VALUE_RENAMES = new HashMap<>() {{
        // HTTP Outcomes
        put("CLIENT_ERROR",           "client_error_4xx");
        put("SUCCESS",                "success_2xx");
        put("SERVER_ERROR",           "server_error_5xx");
        put("REDIRECTION",            "redirection_3xx");
        // JVM Memory Pools
        put("G1 Old Gen",             "heap_old_generation");
        put("G1 Eden Space",          "heap_eden_space");
        put("G1 Survivor Space",      "heap_survivor_space");
        put("CodeCache",              "nonheap_code_cache");
        put("Metaspace",              "nonheap_metaspace");
        put("Compressed Class Space", "nonheap_class_space");
        // Thread States
        put("runnable",               "actively_running");
        put("waiting",                "waiting_indefinitely");
        put("timed-waiting",          "waiting_with_timeout");
        put("blocked",                "blocked_on_lock");
        put("new",                    "not_yet_started");
        put("terminated",             "finished_execution");
    }};
}