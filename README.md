# POS Utils Metrics

A lightweight, annotation-driven metrics library for NgPos services that simplifies Prometheus metrics integration with Spring Boot applications.

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
- [Tag Resolution](#tag-resolution)
- [Output Format](#output-format)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Library Structure](#library-structure)

## Overview

`pos-utils-metrics` provides a clean, declarative way to add Prometheus metrics to your Spring Boot applications. It abstracts away the boilerplate of Micrometer and Prometheus configuration, letting developers focus on business logic.

**Why use this library?**

- Reduce metric tracking code by 84%
- Standardize metrics across all NgPos services
- Zero boilerplate with annotations
- Safe by design - never breaks user code
- Auto-configured via Spring Boot

## Features

- **Annotation-Based Tracking** - Add metrics with `@Metric` annotation
- **Exception Handling** - Track exceptions with `@OnException` and `@Track`
- **Programmatic API** - Use `MetricService` for conditional tracking
- **Clean Output** - Custom `/metrics` endpoint with renamed metrics
- **Configurable Filtering** - Allow only metrics you care about
- **Zero Boilerplate** - Auto-configuration via Spring Boot
- **Type & Timestamp Tags** - Automatically added to every metric
- **Critical Exception Support** - Mark important exceptions for alerts
- **Safe by Design** - Library failures never break user code

## Requirements

- Java 17 or higher
- Spring Boot 3.x
- Micrometer Prometheus Registry

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.kroger.ngpos.utils</groupId>
    <artifactId>pos-utils-metrics</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### 1. Add Configuration
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
      enabled: false      # IMPORTANT: Disable to use our /metrics endpoint
    health:
      enabled: true

metrics:
  additional-prefixes:
    - business.day
    - order
    - payment
```

### 2. Use Annotation

```java
@Metric(
    on   = "business.day.get.active",
    type = MetricType.ALL,
    tags = {"id=#request.id"}
)
@PostMapping("/api/v1/")
public ResponseEntity<String> get(
        @RequestBody Request request) {
    // your business logic
}
```

### 3. Access Metrics

```
http://localhost:8080/metrics
```

## Configuration

### Allowed Metric Prefixes
By default, the library allows these system metrics:

- jvm.memory
- jvm.threads
- http.server
- process.cpu
- system.cpu
- logback

Add your business-specific prefixes in application.yml:

```yaml
metrics:
  additional-prefixes:
    - business.day
    - order.process
    - payment.transaction
```

### Compiler Configuration

Add -parameters flag to your Maven compiler for dynamic tag resolution:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

## Annotations

### @Metric
Tracks success metrics for a method.

```java
@Metric(
    on   = "business.day.get.active",
    type = MetricType.ALL,
    tags = {"dltId=#request.dltId"}
)
public Response getActive(Request request) { }
```

**Parameters:**

| Parameter | Type       | Required | Default | Description                |
|-----------|------------|----------|---------|----------------------------|
| on        | String     | Yes      | -       | Event/metric name          |
| type      | MetricType | No       | ALL     | Metric type                |
| tags      | String[]   | No       | {}      | Custom tags with #paramName support |

**Metric Types:**

| Type    | Description         | Suffix Added      |
|---------|---------------------|-------------------|
| COUNTER | Count occurrences   | _total            |
| TIMER   | Measure duration    | _duration_seconds |
| GAUGE   | Current snapshot    | none              |
| ALL     | Counter + Timer     | _total and _duration_seconds |

### @OnException
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

**Parameters:**

| Parameter | Type     | Required | Default | Description                |
|-----------|----------|----------|---------|----------------------------|
| on        | String   | No       | ""      | Default metric name when no @Track matches |
| tags      | String[] | No       | {}      | Custom tags                |
| ignore    | Class[]  | No       | {}      | Exceptions to skip completely |
| track     | Track[]  | No       | {}      | Specific exception mappings |

**Behavior:**

- If track list is defined: only listed exceptions are tracked
- If no track list and on is defined: all exceptions tracked as on_failure
- Exceptions in ignore list are always skipped

### @Track
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

## MetricService API

For conditional or business-rule-based tracking, use MetricService:

```java
@Service
public class BusinessDayService {

    private final MetricService metricService;

    public BusinessDayService(MetricService metricService) {
        this.metricService = metricService;
    }

    public void process(String storeId) {
        try {
            // business logic

            metricService.countIf(
                response.getStatus() == ACTIVE,
                "business.day.active.found",
                "storeId", storeId
            );
        }
        catch (Exception ex) {
            metricService.trackException(
                "business.day.process.failure",
                ex,
                "storeId", storeId
            );
            throw ex;
        }
    }
}
```

**Available Methods**

| Method                        | Purpose                  | Example                                  |
|-------------------------------|--------------------------|------------------------------------------|
| count(name)                   | Simple counter increment | metricService.count("user.login")        |
| count(name, tags...)          | Counter with tags        | metricService.count("user.login", "type", "oauth") |
| countIf(condition, name, ...) | Conditional counter      | metricService.countIf(isVip, "vip.login")|
| trackException(name, ex, ...) | Track exception          | metricService.trackException("api.failure", ex) |
| trackCritical(name, ex, ...)  | Track critical exception | metricService.trackCritical("db.failure", ex) |
| trackError(name, msg, ...)    | Track error log          | metricService.trackError("validation", "Invalid input") |
| recordTime(name, dur, ...)    | Record duration          | metricService.recordTime("query.time", 250) |

## Tag Resolution

Tags support both constants and dynamic values:

**Constants**

```java
tags = {
    "team=accounting",
    "service=business-day-api",
    "environment=production"
}
```

**Dynamic Values (Method Parameters)**

```java
// Simple parameter
tags = {"storeId=#storeId"}

// Nested field via getter
tags = {"dltId=#request.dltId"}        // calls request.getDltId()

// Multiple dynamic tags
tags = {
    "userId=#request.userId",
    "orderId=#request.orderId",
    "channel=#channel"
}
```

**Mixed (Constants + Dynamic)**

```java
tags = {
    "team=accounting",                    // Constant
    "service=business-day-api",          // Constant
    "dltId=#request.dltId",              // Dynamic
    "storeId=#request.storeId"           // Dynamic
}
```

## Output Format

**Custom Business Metrics**

```prometheus
business_day_get_active_total{
    class="BusinessDayController",
    method="getActiveBusinessDay",
    id="DLT123456",
    status="success",
    type="custom",
    timestamp="2026-05-22T08:08:50.993Z"
} 1.0

business_day_get_active_duration_seconds{
    class="BusinessDayController",
    method="getActiveBusinessDay",
    id="DLT123456",
    status="success",
    type="custom",
    timestamp="2026-05-22T08:08:50.993Z"
} 123.0
```

**System Metrics**

```prometheus
memory_currently_used_bytes{
    area="heap",
    id="heap_old_generation",
    type="jvm",
    timestamp="2026-05-22T08:08:50.993Z"
} 3.58E7

threads_currently_active{
    type="jvm",
    timestamp="2026-05-22T08:08:50.993Z"
} 35.0

application_cpu_usage_percent{
    type="process",
    timestamp="2026-05-22T08:08:50.993Z"
} 0.057
```

**Exception Metrics**

```prometheus
business_day_validation_failure_total{
    class="BusinessDayController",
    method="getActiveBusinessDay",
    id="DLT123456",
    status="failure",
    exception="ValidationException",
    message="Invalid request",
    critical="false",
    type="custom",
    timestamp="2026-05-22T08:08:50.993Z"
} 1.0
```

### Conditional Tracking

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

## Best Practices

### When to Use @Metric
Use @Metric for:

- Public API methods
- Service layer methods
- Repository methods
- Kafka consumers
- Scheduled jobs
- External API calls

Avoid @Metric for:

- Private internal methods
- Utility/calculation methods
- Constructors and setters
- Methods in tight loops

### When to Use MetricService
Use MetricService for:

- Conditional tracking based on business rules
- Multiple metrics from one method
- Result-based metric differentiation
- Custom logic requirements

### Tag Cardinality Warning

```java
// BAD - Creates millions of unique metrics
tags = {
    "userId=#request.userId",        // Millions of users
    "timestamp=#request.timestamp"   // Every request unique
}

// GOOD - Limited unique combinations
tags = {
    "team=accounting",               // Few teams
    "channel=#request.channel",      // POS, Web, Mobile
    "storeId=#request.storeId"       // Hundreds of stores
}
```

### Metric Naming Convention

```
Format: {domain}.{entity}.{action}

Examples:
business.day.get.active
order.payment.processed
inventory.stock.checked
```

### Critical vs Non-Critical Exceptions
Mark exceptions as critical when they require immediate attention:

```java
@Track(type     = DataAccessException.class,
       metric   = "business.day.db.failure",
       critical = true)              // DB failures need immediate alert

@Track(type   = ValidationException.class,
       metric = "business.day.validation.failure"
       )                              // Validation failures are expected
```

## Troubleshooting

### Metrics Not Showing Up
**Issue:** Custom metrics don't appear in /metrics endpoint.

**Solution:** Add your metric prefix to application.yml:

```yaml
metrics:
  enabled: true
  additional-prefixes:
    - your.metric.prefix
```

### 404 on /metrics
**Issue:** /metrics endpoint returns 404 or 500.

**Solution:** Ensure default Prometheus endpoint is disabled:

```yaml
management:
  endpoint:
    prometheus:
      enabled: false
```

### Aspect Not Triggering
**Issue:** @Metric annotation doesn't trigger.

**Solution:** Ensure:

- Method is public
- Method is called externally (not from same class - self-invocation bypasses AOP)
- Class is a Spring bean (@Component, @Service, @RestController)

### Tag Resolution Failing
**Issue:** Dynamic tags show unresolved.

**Solution:** Ensure:

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

### Library Doesn't Load
**Issue:** Library beans not created.

**Solution:** Check:

- Dependency added correctly in pom.xml
- Spring Boot version is 3.x
- No bean conflicts in your application

### Conflict with Other Metric Libraries
**Issue:** Multiple metric endpoints registered.

**Solution:** Disable other metric exposers:

```yaml
management:
  endpoint:
    prometheus:
      enabled: false
    metrics:
      enabled: false
```

## Library Structure

```
com.kroger.metrics
├── annotation/
│   ├── Metric.java          - Main success tracking annotation
│   ├── MetricType.java      - COUNTER, TIMER, GAUGE, ALL
│   ├── OnException.java     - Exception tracking annotation
│   └── Track.java           - Per-exception metric mapping
├── aspect/
│   ├── MetricAspect.java    - Handles @Metric
│   └── OnExceptionAspect.java - Handles @OnException
├── service/
│   └── MetricService.java   - Programmatic metric API
├── config/
│   ├── MetricsAutoConfiguration.java - Spring auto-configuration
│   ├── MetricsConfig.java   - Meter filtering and prefixes
│   └── MetricsController.java - Custom /metrics endpoint
└── constants/
    └── MetricsConstants.java - Metric and tag rename mappings
```

### Kill Switch Concept in Auto-Configuration

The auto-configuration now uses a **Kill Switch** concept to allow dynamic disabling of all custom metrics collection at runtime. This is implemented in `MetricsAutoConfiguration` and related beans. When the kill switch is enabled (via configuration or environment variable), all metric aspects and services become no-ops, ensuring zero impact on application performance or behavior. This is useful for emergency disables, troubleshooting, or compliance scenarios.
