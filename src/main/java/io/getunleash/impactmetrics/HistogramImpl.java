package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class HistogramImpl implements Histogram {
    private final BucketMetricOptions options;
    private final List<Double> buckets;
    private final ConcurrentHashMap<String, HistogramData> values = new ConcurrentHashMap<>();

    private static final List<Double> DEFAULT_BUCKETS =
            List.of(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0);

    HistogramImpl(BucketMetricOptions options) {
        this.options = options;
        List<Double> inputBuckets = options.getBuckets();
        if (inputBuckets == null || inputBuckets.isEmpty()) {
            inputBuckets = DEFAULT_BUCKETS;
        }

        List<Double> sorted =
                inputBuckets.stream()
                        .filter(d -> d != Double.POSITIVE_INFINITY)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
        sorted.add(Double.POSITIVE_INFINITY);
        this.buckets = Collections.unmodifiableList(sorted);
    }

    @Override
    public void observe(double value) {
        observe(value, null);
    }

    @Override
    public void observe(double value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.compute(
                key,
                (k, data) -> {
                    if (data == null) {
                        data = new HistogramData(0, 0.0, buckets);
                    }
                    data.count++;
                    data.sum += value;
                    for (Map.Entry<Double, Long> entry : data.buckets.entrySet()) {
                        if (value <= entry.getKey()) {
                            entry.setValue(entry.getValue() + 1);
                        }
                    }
                    return data;
                });
    }

    @Override
    public void restore(BucketMetricSample sample) {
        String key = CounterImpl.getLabelKey(sample.getLabels());
        HistogramData data = new HistogramData(sample.getCount(), sample.getSum(), buckets);

        for (HistogramBucket bucket : sample.getBuckets()) {
            data.buckets.put(bucket.getLe(), bucket.getCount());
        }
        values.put(key, data);
    }

    public CollectedMetric collect() {
        List<MetricSample> samples = new ArrayList<>();

        for (Map.Entry<String, HistogramData> entry : values.entrySet()) {
            String key = entry.getKey();
            HistogramData data = entry.getValue();

            List<HistogramBucket> bucketSamples = new ArrayList<>();
            // Ensure we export all buckets even if count 0?
            // Node SDK exports all buckets.
            for (Double le : buckets) {
                bucketSamples.add(new HistogramBucket(le, data.buckets.getOrDefault(le, 0L)));
            }

            samples.add(
                    new BucketMetricSample(
                            CounterImpl.parseLabelKey(key), data.count, data.sum, bucketSamples));
        }

        values.clear();

        if (samples.isEmpty()) {
            // Return zeroed sample
            List<HistogramBucket> zeroBuckets = new ArrayList<>();
            for (Double le : buckets) {
                zeroBuckets.add(new HistogramBucket(le, 0L));
            }
            samples.add(new BucketMetricSample(Collections.emptyMap(), 0L, 0.0, zeroBuckets));
        }

        return new CollectedMetric(
                options.getName(), options.getHelp(), MetricType.HISTOGRAM, samples);
    }
}
