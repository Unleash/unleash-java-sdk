package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMetricRegistry implements ImpactMetricRegistry, ImpactMetricsDataSource {
    private final Map<String, CounterImpl> counters = new ConcurrentHashMap<>();
    private final Map<String, GaugeImpl> gauges = new ConcurrentHashMap<>();

    @Override
    public Counter counter(MetricOptions options) {
        return counters.computeIfAbsent(options.getName(), name -> new CounterImpl(options));
    }

    @Override
    public Gauge gauge(MetricOptions options) {
        return gauges.computeIfAbsent(options.getName(), name -> new GaugeImpl(options));
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
        return collected;
    }

    @Override
    public void restore(List<CollectedMetric> metrics) {
        for (CollectedMetric metric : metrics) {
            if (metric.getType() == MetricType.COUNTER) {
                Counter counter = counter(new MetricOptions(metric.getName(), metric.getHelp()));
                for (NumericMetricSample sample : metric.getSamples()) {
                    counter.inc(sample.getValue(), sample.getLabels());
                }
            } else if (metric.getType() == MetricType.GAUGE) {
                Gauge gauge = gauge(new MetricOptions(metric.getName(), metric.getHelp()));
                for (NumericMetricSample sample : metric.getSamples()) {
                    gauge.set(sample.getValue(), sample.getLabels());
                }
            }
        }
    }
}
