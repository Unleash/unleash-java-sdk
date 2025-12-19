package io.getunleash.impactmetrics;

import io.getunleash.lang.Nullable;

public interface ImpactMetricRegistry {
    Counter counter(MetricOptions options);

    Gauge gauge(MetricOptions options);

    Histogram histogram(BucketMetricOptions options);

    @Nullable
    Counter getCounter(String name);

    @Nullable
    Gauge getGauge(String name);

    @Nullable
    Histogram getHistogram(String name);
}
