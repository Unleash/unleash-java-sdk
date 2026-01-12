package io.getunleash.impactmetrics;

import java.util.List;
import java.util.Map;
import java.util.Objects;

class BucketMetricSample implements MetricSample {
    private final Map<String, String> labels;
    private final long count;
    private final double sum;
    private final List<HistogramBucket> buckets;

    BucketMetricSample(
            Map<String, String> labels, long count, double sum, List<HistogramBucket> buckets) {
        this.labels = labels;
        this.count = count;
        this.sum = sum;
        this.buckets = buckets;
    }

    @Override
    public Map<String, String> getLabels() {
        return labels;
    }

    long getCount() {
        return count;
    }

    double getSum() {
        return sum;
    }

    List<HistogramBucket> getBuckets() {
        return buckets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BucketMetricSample that = (BucketMetricSample) o;
        return count == that.count
                && Double.compare(that.sum, sum) == 0
                && Objects.equals(labels, that.labels)
                && Objects.equals(buckets, that.buckets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels, count, sum, buckets);
    }

    @Override
    public String toString() {
        return "BucketMetricSample{"
                + "labels="
                + labels
                + ", count="
                + count
                + ", sum="
                + sum
                + ", buckets="
                + buckets
                + '}';
    }
}
