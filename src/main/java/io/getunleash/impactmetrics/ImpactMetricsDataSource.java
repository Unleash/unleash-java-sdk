package io.getunleash.impactmetrics;

import java.util.List;

interface ImpactMetricsDataSource {
    List<CollectedMetric> collect();

    void restore(List<CollectedMetric> metrics);
}
