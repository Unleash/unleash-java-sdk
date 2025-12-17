package io.getunleash.impactmetrics;

import java.util.List;

public class BucketMetricOptions extends MetricOptions {
    private final List<Double> buckets;

    public BucketMetricOptions(String name, String help, List<Double> buckets) {
        super(name, help);
        this.buckets = buckets;
    }

    public BucketMetricOptions(
            String name, String help, List<String> labelNames, List<Double> buckets) {
        super(name, help, labelNames);
        this.buckets = buckets;
    }

    public List<Double> getBuckets() {
        return buckets;
    }
}
