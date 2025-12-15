package io.getunleash.impactmetrics;

public interface ImpactMetricRegistry {
    Counter counter(MetricOptions options);
}
