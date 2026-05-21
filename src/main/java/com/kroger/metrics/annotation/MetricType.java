package com.kroger.metrics.annotation;


public enum MetricType
{
    COUNTER,    // Count occurrences
    TIMER,      // Measure duration
    GAUGE,      // Current value
    ALL         // Counter + Timer together
}