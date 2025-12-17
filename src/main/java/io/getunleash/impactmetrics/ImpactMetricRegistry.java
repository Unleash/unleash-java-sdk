package io.getunleash.impactmetrics;

public interface ImpactMetricRegistry {
    Counter counter(MetricOptions options);

    Gauge gauge(MetricOptions options);

    Histogram histogram(BucketMetricOptions options);
}
