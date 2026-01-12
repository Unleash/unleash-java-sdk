package io.getunleash.impactmetrics;

import java.util.List;

class BucketMetricOptions extends MetricOptions {
    private final List<Double> buckets;

    BucketMetricOptions(String name, String help, List<Double> buckets) {
        super(name, help);
        this.buckets = buckets;
    }

    BucketMetricOptions(String name, String help, List<String> labelNames, List<Double> buckets) {
        super(name, help, labelNames);
        this.buckets = buckets;
    }

    List<Double> getBuckets() {
        return buckets;
    }
}
