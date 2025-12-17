package io.getunleash.impactmetrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class HistogramData {
    long count;
    double sum;
    final Map<Double, Long> buckets = new HashMap<>();

    HistogramData(long count, double sum, List<Double> inputBuckets) {
        this.count = count;
        this.sum = sum;
        for (Double bucket : inputBuckets) {
            buckets.put(bucket, 0L);
        }
    }
}
