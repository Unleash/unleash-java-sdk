package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMetricRegistry implements ImpactMetricRegistryAndDataSource {
    private final Map<String, CounterImpl> counters = new ConcurrentHashMap<>();
    private final Map<String, GaugeImpl> gauges = new ConcurrentHashMap<>();
    private final Map<String, HistogramImpl> histograms = new ConcurrentHashMap<>();

    @Override
    public Counter counter(MetricOptions options) {
        return counters.computeIfAbsent(options.getName(), name -> new CounterImpl(options));
    }

    @Override
    public Gauge gauge(MetricOptions options) {
        return gauges.computeIfAbsent(options.getName(), name -> new GaugeImpl(options));
    }

    @Override
    public Histogram histogram(BucketMetricOptions options) {
        return histograms.computeIfAbsent(options.getName(), name -> new HistogramImpl(options));
    }

    @Override
    public Counter getCounter(String name) {
        return counters.get(name);
    }

    @Override
    public Gauge getGauge(String name) {
        return gauges.get(name);
    }

    @Override
    public Histogram getHistogram(String name) {
        return histograms.get(name);
    }

    @Override
    public List<CollectedMetric> collect() {
        List<CollectedMetric> collected = new ArrayList<>();
        counters.values().stream()
                .map(CounterImpl::collect)
                .filter(m -> !m.getSamples().isEmpty())
                .forEach(collected::add);
        gauges.values().stream()
                .map(GaugeImpl::collect)
                .filter(m -> !m.getSamples().isEmpty())
                .forEach(collected::add);
        histograms.values().stream()
                .map(HistogramImpl::collect)
                .filter(m -> !m.getSamples().isEmpty())
                .forEach(collected::add);
        return collected;
    }

    @Override
    public void restore(List<CollectedMetric> metrics) {
        for (CollectedMetric metric : metrics) {
            if (metric.getType() == MetricType.COUNTER) {
                Counter counter = counter(new MetricOptions(metric.getName(), metric.getHelp()));
                for (MetricSample sample : metric.getSamples()) {
                    if (sample instanceof NumericMetricSample) {
                        NumericMetricSample numericSample = (NumericMetricSample) sample;
                        counter.inc(numericSample.getValue(), numericSample.getLabels());
                    }
                }
            } else if (metric.getType() == MetricType.GAUGE) {
                Gauge gauge = gauge(new MetricOptions(metric.getName(), metric.getHelp()));
                for (MetricSample sample : metric.getSamples()) {
                    if (sample instanceof NumericMetricSample) {
                        NumericMetricSample numericSample = (NumericMetricSample) sample;
                        gauge.set(numericSample.getValue(), numericSample.getLabels());
                    }
                }
            } else if (metric.getType() == MetricType.HISTOGRAM) {
                List<Double> buckets = new ArrayList<>();
                boolean foundBuckets = false;
                for (MetricSample sample : metric.getSamples()) {
                    if (sample instanceof BucketMetricSample) {
                        BucketMetricSample bms = (BucketMetricSample) sample;
                        for (HistogramBucket hb : bms.getBuckets()) {
                            buckets.add(hb.getLe());
                        }
                        foundBuckets = true;
                        break;
                    }
                }

                if (foundBuckets) {
                    Histogram histogram =
                            histogram(
                                    new BucketMetricOptions(
                                            metric.getName(), metric.getHelp(), buckets));
                    for (MetricSample sample : metric.getSamples()) {
                        if (sample instanceof BucketMetricSample) {
                            histogram.restore((BucketMetricSample) sample);
                        }
                    }
                }
            }
        }
    }
}
