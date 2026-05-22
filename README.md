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
- [Programmatic API](#programmatic-api)
- [Output Format](#output-format)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## Overview

`pos-utils-common-metrics` provides a clean, declarative way to add Prometheus metrics to your Spring Boot applications. It abstracts away the boilerplate of Micrometer and Prometheus configuration, letting developers focus on business logic.

## Features

- **Annotation-Based Tracking** - Add metrics with simple annotations
- **Exception Handling** - Track specific exceptions or all exceptions
- **Programmatic API** - Use `MetricService` for conditional tracking
- **Clean Output** - Custom `/metrics` endpoint with renamed metrics
- **Configurable Filtering** - Allow only metrics you care about
- **Zero Boilerplate** - Auto-configuration via Spring Boot
- **Type & Timestamp Tags** - Automatically added to every metric
- **Critical Exception Support** - Mark important exceptions for alerts

## Requirements

- Java 17 or higher
- Spring Boot 3.x
- Micrometer Prometheus Registry

## Installation

Add the dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>com.kroger</groupId>
    <artifactId>pos-utils-metrics</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Quick Start

1. **Configure Allowed Metric Prefixes**
   In your `application.yml`:
   ```yaml
   metrics:
     additional-prefixes:
       - business.day
       - order
       - payment
   management:
     endpoints:
       web:
         exposure:
           include: health
     endpoint:
       prometheus:
         enabled: false
   ```

2. **Enable Metrics Auto-Configuration**
   The library auto-configures itself if on the classpath. No extra setup is needed.

3. **Add Annotations**
   Annotate methods or classes with `@Track`, `@Metric`, or `@OnException` to track metrics. Example:
   ```java
   @Track(name = "order.placed", type = MetricType.COUNTER)
   public void placeOrder() {
       // ...
   }
   ```

4. **Expose Metrics Endpoint**
   Metrics are available at `/metrics`.

## Configuration

- `metrics.additional-prefixes`: List of allowed metric prefixes. Only metrics starting with these will be exposed.
- `management.endpoint.prometheus.enabled`: Should be `false` to avoid duplicate endpoints.
- `management.endpoints.web.exposure.include`: Should include `health` for basic health checks.

## Annotations

- `@Track`: Track method execution as a metric.
- `@Metric`: Fine-grained metric annotation.
- `@OnException`: Track exceptions as metrics.

## Programmatic API

Use `MetricService` to record metrics in code:
```java
@Autowired
private MetricService metricService;

metricService.increment("order.placed");
metricService.record("payment.duration", duration, MetricType.TIMER);
```

## Output Format

Metrics are exposed in Prometheus format at `/metrics`. Example:
```
# TYPE order_placed counter
order_placed{type="COUNTER",timestamp="..."} 1
```

## Examples

```java
@Track(name = "business.day.opened", type = MetricType.COUNTER)
public void openBusinessDay() { ... }

@OnException(name = "order.failed", exception = OrderException.class)
public void placeOrder() throws OrderException { ... }
```

## Troubleshooting

- **No metrics exposed?** Ensure your metric names start with an allowed prefix.
- **Duplicate endpoints?** Disable the default Prometheus endpoint as shown above.
- **Metrics not incrementing?** Check annotation placement and ensure methods are public.

---

For more details, see the source code and JavaDocs.