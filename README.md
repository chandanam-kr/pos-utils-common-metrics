# POS Utils Metrics — Developer Guide

A lightweight, annotation-driven metrics library for NGPOS services that simplifies Prometheus metrics integration with Spring Boot applications. YAML-driven filtering, auto-tagging, and a runtime kill switch for instant disabling — all with zero boilerplate.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Annotations](#annotations)
  - [@Metric](#metric)
  - [@OnException](#onexception)
  - [@Track](#track)
- [MetricService API](#metricservice-api)
- [Auto-Added Tags](#auto-added-tags)
- [Tag Resolution](#tag-resolution)
- [Filtering Modes](#filtering-modes)
- [Output Format](#output-format)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Kill Switch & NoOpMetricService](#kill-switch-noopservice)

## Overview

`pos-utils-metrics` provides a clean, declarative way to add Prometheus metrics to your Spring Boot applications. It abstracts away the boilerplate of Micrometer and Prometheus configuration, letting developers focus on business logic.

**Why use this library?**

- ✅ Reduce metric tracking code by 84%
- ✅ Standardize metrics across all NGPOS services
- ✅ Zero boilerplate with annotations
- ✅ YAML-driven filtering (no Java changes to add/remove metrics)
- ✅ Auto-tagging with app, type, and timestamp
- ✅ Multi-app safe — app tag uniquely identifies each service
- ✅ Safe by design — never breaks user code
- ✅ Auto-configured via Spring Boot
- ✅ Runtime kill switch disables all metrics safely (NoOpMetricService)

## Features

- **Annotation-Based Tracking** - Add metrics with `@Metric` annotation
- **Exception Handling** - Track exceptions with `@OnException` and `@Track`
- **Programmatic API** - Use `MetricService` for conditional tracking
- **YAML-Driven Filtering** - Whitelist model, no accidental metric leakage
- **Custom Categories** - Map prefixes to semantic type names (e.g., business-day-service)
- **Standard Prometheus Names** - Works with any Grafana dashboard
- **Auto-Added Tags** - app, type, and timestamp on every metric
- **Critical Exception Support** - Mark important exceptions for alerts
- **Unfiltered Mode** - One-flag toggle to expose everything (for debugging)
- **Safe by Design** - Library failures never break user code

## Requirements

- Java 17 or higher
- Spring Boot 3.x or higher
- Micrometer 1.12+
- Micrometer Prometheus Registry

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.kroger.ngpos.utils</groupId>
    <artifactId>pos-utils-common-metrics</artifactId>
    <version>1.0.0</version>
</dependency>
```
The library auto-configures itself on startup. No @Import or @EnableMetrics needed.

## Quick Start

1. Add Configuration
Configure in your application.yml:

```yaml

management:
  endpoints:
    web:
      base-path: "/actuator"
      exposure:
        include: health
  endpoint:
    prometheus:
      enabled: false          # IMPORTANT: Disable to use our /metrics endpoint
    health:
      enabled: true

metrics:
  enabled: true
  categories: [http, jvm, system, process, logback]
  custom-categories:
    business-day-service: [ISA]
```
2. Use Annotation

```java
@Metric(
    on   = "business.day.get.active",
    type = MetricType.ALL,
    tags = {"id=#request.id"}
)
@PostMapping("/api/v1/")
public ResponseEntity<String> get(@RequestBody Request request) {
    // your business logic
}
```
3. Access Metrics

```
http://localhost:8080/metrics
```
That's it. ✅

## Configuration

Complete Reference

```yaml
info:
  app:
    name: my-service              # used as "app" tag value

metrics:
  # Master switch (default: true)
  enabled: true

  # Bypass all filtering and expose every metric (default: false)
  # Useful for development / debugging
  unfiltered: false

  # Standard categories — name acts as both prefix AND type tag value
  # Example: "http" allows all http.* metrics and tags them with type="http"
  categories: [http, jvm, system, process, logback]

  # Custom categories — name is the type tag, value lists prefixes to match
  # Example: business-day-service category catches all ISA.* metrics
  #          and tags them with type="business-day-service"
  custom-categories:
    business-day-service: [ISA]
    payments: [payment.gateway, refund]
    orders: [order]
```
Configuration Fields

| Field             | Type    | Default | Description                                 |
|-------------------|---------|---------|---------------------------------------------|
| enabled           | boolean | true    | Master switch. When false, library does nothing (NoOp). |
| unfiltered        | boolean | false   | When true, exposes ALL metrics (no filtering) |
| categories        | list    | []      | Simple categories (name = prefix = type)    |
| custom-categories | map     | {}      | Custom categories with explicit prefix overrides |

When to Use categories vs custom-categories

| Use categories when...         | Use custom-categories when...         |
|-------------------------------|---------------------------------------|
| Type name = prefix name        | Type name ≠ prefix name               |
| Standard tech categories (jvm, http, system) | Business/service categories with custom naming |
| Listing http alone is enough   | You need specific prefixes per category |

Example:

```yaml
metrics:
  # All metrics starting with "http" → type="http"
  categories: [http, jvm]

  # All metrics starting with "ISA" → type="business-day-service"
  custom-categories:
    business-day-service: [ISA]
```

Compiler Configuration
Add the -parameters flag to your Maven compiler for dynamic tag resolution:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```
🏷 Annotations
@Metric
Tracks success metrics for a method.

```java
@Metric(
    on   = "business.day.get.active",
    type = MetricType.ALL,
    tags = {"dltId=#request.dltId"}
)
public Response getActive(Request request) { }
```
Parameters:

| Parameter | Type       | Required | Default | Description                |
|-----------|------------|----------|---------|----------------------------|
| on        | String     | Yes      | -       | Event/metric name          |
| type      | MetricType | No       | ALL     | Metric type                |
| tags      | String[]   | No       | {}      | Custom tags with #paramName support |

Metric Types:

| Type    | Description         | Suffix Added      |
|---------|---------------------|-------------------|
| COUNTER | Count occurrences   | _total            |
| TIMER   | Measure duration    | _duration_seconds |
| GAUGE   | Current snapshot    | none              |
| ALL     | Counter + Timer     | _total and _duration_seconds |

Auto-added tags by @Metric:

- class — enclosing class
- method — method name
- status — success, failure, client_error, or server_error

Status resolution for ResponseEntity:
- 2xx → success
- 4xx → client_error
- 5xx → server_error
- Other → unknown

Status resolution for other return types:
- No exception → success
- Exception thrown → failure

@OnException
Tracks exceptions thrown by a method.

```java
@OnException(
    on     = "business.day.get.active",
    tags   = {"dltId=#request.dltId"},
    ignore = {EntityNotFoundException.class},
    track  = {
        @Track(type   = ValidationException.class,
               metric = "business.day.validation.failure"),
        @Track(type     = DataAccessException.class,
               metric   = "business.day.db.failure",
               critical = true)
    }
)
public Response getActive(Request request) { }
```
Parameters:

| Parameter | Type     | Required | Default | Description                |
|-----------|----------|----------|---------|----------------------------|
| on        | String   | No       | ""      | Default metric name when no @Track matches |
| tags      | String[] | No       | {}      | Custom tags                |
| ignore    | Class[]  | No       | {}      | Exceptions to skip completely |
| track     | Track[]  | No       | {}      | Specific exception mappings |

Behavior:
- Exception in ignore list → not tracked
- Exception matches a @Track.type → uses that @Track.metric name
- Otherwise → uses <on>_failure_total

Auto-added tags by @OnException:
- class, method
- status="failure"
- exception — exception class simple name
- message — exception message (or "no_message")
- critical — "true" or "false"

@Track
Maps a specific exception type to a custom metric name. Used inside @OnException.track().

```java
@Track(
    type     = ValidationException.class,
    metric   = "business.day.validation.failure",
    critical = false
)
```

| Parameter | Type   | Required | Default | Description                |
|-----------|--------|----------|---------|----------------------------|
| type      | Class  | Yes      | -       | Exception type to match    |
| metric    | String | Yes      | -       | Custom metric name         |
| critical  | bool   | No       | false   | If true, logs as ERROR instead of WARN |

🔌 MetricService API
For conditional or business-rule-based tracking, use MetricService:

```java
@Service
public class BusinessDayService {

    private final MetricService metricService;

    public BusinessDayService(MetricService metricService) {this.metricService = metricService;}

    public void process(String storeId) {
        try {
            // business logic
            metricService.countIf(response.getStatus() == ACTIVE, "business.day.active.found", "storeId", storeId
            );
        }
        catch (Exception ex) {metricService.trackException("business.day.process.failure", ex, "storeId", storeId);
            throw ex;
        }
    }
}
```
Available Methods:

| Method                        | Description                                 |
|-------------------------------|---------------------------------------------|
| count(name)                   | Simple counter increment                    |
| count(name, tags...)          | Counter with key, value, key, value pairs   |
| countIf(condition, name, tags...) | Counter only if condition is true         |
| trackException(name, throwable, tags...) | Track exception, log as WARN      |
| trackCritical(name, throwable, tags...)  | Track exception, log as ERROR    |
| trackError(name, message, tags...)       | Track an error log entry         |
| recordTime(name, durationMs, tags...)    | Record a duration in milliseconds|

Tag format: All tag args are passed as alternating key, value strings:

```java
metricService.count("checkout.attempt", "region", "us", "tier", "premium");
//                                       └────┬────┘  └────┬───────┘
//                                       key1 value1  key2  value2
```

Auto-added tags by MetricService:
- class — detected from stack trace (caller class)
- method — detected from stack trace (caller method)
- For exception methods: status, exception, message, critical

Safety guarantee:
All MetricService operations are wrapped in error handling. If metric recording fails (invalid tag, registry error, etc.), the exception is caught and logged — your business logic continues unaffected.

🎯 Auto-Added Tags
Every metric automatically receives these tags without any code or annotation effort:

| Tag      | Source                                 | Example                |
|----------|----------------------------------------|------------------------|
| app      | info.app.name from application.yml      | app="my-service"      |
| type     | YAML category name matching the metric prefix | type="business-day-service" |
| timestamp| ISO-8601 scrape timestamp              | timestamp="2024-01-15T10:30:00Z" |

Plus from @Metric / @OnException aspects:

| Tag      | When added                             |
|----------|----------------------------------------|
| class    | Always                                 |
| method   | Always                                 |
| status   | Always (success, failure, client_error, server_error) |
| exception| On exception                           |
| message  | On exception                           |
| critical | On exception when critical=true         |

🔍 Tag Resolution
Tags support both constants and dynamic values.

Constants
```java
tags = {"team=accounting","service=business-day-api","environment=dev"}
```
Dynamic Values (Method Parameters)
```java
// Simple parameter
tags = {"storeId=#storeId"}

// Nested field via getter
tags = {"dltId=#request.dltId"}        // calls request.getDltId()

// Multiple dynamic tags
tags = {"userId=#request.userId","orderId=#request.orderId","channel=#channel"}
```
Mixed (Constants + Dynamic)
```java
tags = {
    "team=accounting",                    // Constant
    "service=business-day-api",          // Constant
    "dltId=#request.dltId",              // Dynamic
    "storeId=#request.storeId"           // Dynamic
}
```

🎛 Filtering Modes
Filtered mode (default)
Only metrics matching configured prefixes are exposed:

```yaml
metrics:
  enabled: true
  categories: [http, jvm]
```
✅ Allowed: http.server.requests, jvm.memory.used

❌ Denied: system.cpu.usage, tomcat.sessions.active

Unfiltered mode
Bypass all filtering — exposes every metric. Useful for development / exploration:

```yaml
metrics:
  enabled: true
  unfiltered: true
```
In this mode, type tag is auto-derived from the metric's first dot-segment:
- jvm.memory.used → type="jvm"
- tomcat.sessions.active → type="tomcat"

Library disabled (Kill Switch)
```yaml
metrics:
  enabled: false
```
- No aspects run
- No /metrics endpoint
- MetricService falls back to NoOpMetricService (user code keeps working without errors)

📤 Output Format
Custom Business Metrics
```prometheus
business_day_get_active_total{
    app="my-service",
    class="BusinessDayController",
    method="getActiveBusinessDay",
    id="DLT123456",
    status="success",
    type="business-day-service",
    timestamp="2026-05-22T08:08:50.993Z"
} 1.0

business_day_get_active_duration_seconds{
    app="my-service",
    class="BusinessDayController",
    method="getActiveBusinessDay",
    id="DLT123456",
    status="success",
    type="business-day-service",
    timestamp="2026-05-22T08:08:50.993Z"
} 123.0
```
System Metrics
```prometheus
jvm_memory_used_bytes{
    app="my-service",
    area="heap",
    id="G1 Old Gen",
    type="jvm",
    timestamp="2026-05-22T08:08:50.993Z"
} 3.58E7

jvm_threads_live_threads{
    app="my-service",
    type="jvm",
    timestamp="2026-05-22T08:08:50.993Z"
} 35.0

process_cpu_usage{
    app="my-service",
    type="process",
    timestamp="2026-05-22T08:08:50.993Z"
} 0.057
```
Exception Metrics
```prometheus
business_day_validation_failure_total{
    app="my-service",
    class="BusinessDayController",
    method="getActiveBusinessDay",
    id="DLT123456",
    status="failure",
    exception="ValidationException",
    message="Invalid request",
    critical="false",
    type="business-day-service",
    timestamp="2026-05-22T08:08:50.993Z"
} 1.0
```
Conditional Tracking Example
```java
@Service
public class BusinessDayService {

    private final MetricService metricService;

    public BusinessDayService(MetricService metricService) {
        this.metricService = metricService;
    }

    @Metric(
        on   = "business.day.get.active",
        type = MetricType.ALL,
        tags = {"storeId=#storeId"}
    )
    public BusinessDayResponse getActive(String storeId) {
        BusinessDayResponse response = repository.findActive(storeId);

        // Track only when status is ACTIVE
        metricService.countIf(
            response != null && response.getStatus() == ACTIVE,
            "business.day.active.found",
            "storeId", storeId
        );

        // Track stale data detection
        metricService.countIf(
            response != null && response.isStale(),
            "business.day.stale.detected",
            "storeId", storeId,
            "age", String.valueOf(response.getAge())
        );

        return response;
    }
}
```

✅ Best Practices
When to Use @Metric
✅ Use @Metric for:
- Public API methods
- Service layer methods
- Repository methods
- Kafka consumers
- Scheduled jobs
- External API calls

❌ Avoid @Metric for:
- Private internal methods
- Utility/calculation methods
- Constructors and setters
- Methods in tight loops

When to Use MetricService
Use MetricService for:
- Conditional tracking based on business rules
- Multiple metrics from one method
- Result-based metric differentiation
- Custom logic requirements

Tag Cardinality Warning
```java
// ❌ BAD - Creates millions of unique time series
tags = {
    "userId=#request.userId",        // Millions of users
    "timestamp=#request.timestamp"   // Every request unique
}

// ✅ GOOD - Limited unique combinations
tags = {
    "team=accounting",               // Few teams
    "channel=#request.channel",      // POS, Web, Mobile
    "storeId=#request.storeId"       // Hundreds of stores
}
```

Metric Naming Convention
```
Format: {domain}.{entity}.{action}

Examples:
business.day.get.active
order.payment.processed
inventory.stock.checked
```

Critical vs Non-Critical Exceptions
Mark exceptions as critical when they require immediate attention:

```java
@Track(type     = DataAccessException.class,
       metric   = "business.day.db.failure",
       critical = true)              // DB failures need immediate alert

@Track(type   = ValidationException.class,
       metric = "business.day.validation.failure"
       )                              // Validation failures are expected
```

Choosing Category Names
| Use case                | Naming convention         |
|-------------------------|--------------------------|
| Standard tech metrics   | Use tech name: http, jvm, system |
| Business service metrics| Use service name: payments, orders, inventory |
| Domain grouping         | Use domain: finance, operations, customer |
| Avoid generic words     | ❌ business, service, stuff, core |

🔧 Troubleshooting
Metrics Not Showing Up
Issue: Custom metrics don't appear in /metrics endpoint.

Solution: Add your metric prefix to a category in application.yml:

```yaml
metrics:
  custom-categories:
    your-area: [your.metric.prefix]
```
Check the startup logs:

```
INFO  Metrics filter initialized — categories: [...], effective prefixes: [...]
```
If your metric's prefix isn't in effective prefixes, it's being denied.

My metric has type="custom" instead of expected type
This means the metric's prefix didn't match any category and fell back to the first segment of the metric name.

Fix: Add it to a named custom-categories entry:

```yaml
metrics:
  custom-categories:
    business: [my.prefix]    # ← now type="business"
```

The app tag shows "unknown"
You'll see this warning at startup:

```
WARN  Could not resolve 'app' tag. Set 'info.app.name' in application.yml. Defaulting to 'unknown'.
```
Fix: Set info.app.name in your application.yml:

```yaml
info:
  app:
    name: my-service
```

404 on /metrics
Issue: /metrics endpoint returns 404 or 500.

Solution: Ensure the default Prometheus endpoint is disabled:

```yaml
management:
  endpoint:
    prometheus:
      enabled: false
```

Aspect Not Triggering
Issue: @Metric or @OnException annotation doesn't trigger.

Solution: Ensure:
- Method is public
- Method is called externally (not from within the same class — self-invocation bypasses AOP)
- Class is a Spring bean (@Component, @Service, @RestController)

Tag Resolution Failing
Issue: Dynamic tags show unresolved.

Solution: Ensure:
- Parameter name matches exactly (case-sensitive)
- Field has a public getter
- Compile with -parameters flag for parameter names

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

"Metric recording failed" warnings in logs
This is safe behavior — the library catches all metric errors to prevent them from breaking your code. Common causes:
- Invalid tag key/value (null, empty, special characters)
- Cardinality issues (too many unique tag combinations)
Inspect the error message; it'll point to the issue.

Library Doesn't Load
Issue: Library beans not created.

Solution: Check:
- Dependency added correctly in pom.xml
- Spring Boot version is 3.x
- No bean conflicts in your application

Conflict with Other Metric Libraries
Issue: Multiple metric endpoints registered.

Solution: Disable other metric exposers:

```yaml
management:
  endpoint:
    prometheus:
      enabled: false
    metrics:
      enabled: false
```

🛡 Kill Switch & NoOpMetricService
The auto-configuration uses a Kill Switch concept to allow dynamic disabling of all custom metrics collection at runtime.

How It Works
Set metrics.enabled: false in your configuration or environment to instantly disable all metrics. When disabled:
- No metric aspects run
- No /metrics endpoint is exposed
- MetricService becomes a NoOpMetricService (silently ignores all calls)
- Zero impact on application performance or behavior
- Your code continues to compile and run normally — no NullPointerException

When to Use
- Emergency disables — disable instantly without code changes
- Troubleshooting — rule out metrics as a cause of issues
- Compliance scenarios — temporarily stop metric collection
- Testing — disable in unit tests for isolation
```