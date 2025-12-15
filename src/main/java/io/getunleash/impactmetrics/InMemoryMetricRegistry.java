package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMetricRegistry implements ImpactMetricRegistry, ImpactMetricsDataSource {
    private final Map<String, CounterImpl> counters = new ConcurrentHashMap<>();

    @Override
    public Counter counter(MetricOptions options) {
        return counters.computeIfAbsent(
                options.getName(), name -> new CounterImpl(name, options.getHelp()));
    }

    @Override
    public List<CollectedMetric> collect() {
        List<CollectedMetric> collected = new ArrayList<>();
        counters.values().forEach(c -> collected.add(c.collect()));
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
            }
        }
    }
}
