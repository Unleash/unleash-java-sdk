package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class GaugeImpl implements Gauge {
    private final MetricOptions options;
    private final ConcurrentHashMap<String, Double> values = new ConcurrentHashMap<>();

    GaugeImpl(MetricOptions options) {
        this.options = options;
    }

    @Override
    public void set(double value) {
        set(value, null);
    }

    @Override
    public void set(double value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.put(key, value);
    }

    @Override
    public void inc() {
        inc(1.0, null);
    }

    @Override
    public void inc(double value) {
        inc(value, null);
    }

    @Override
    public void inc(double value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.compute(key, (k, current) -> (current == null ? 0.0 : current) + value);
    }

    @Override
    public void dec() {
        dec(1.0, null);
    }

    @Override
    public void dec(double value) {
        dec(value, null);
    }

    @Override
    public void dec(double value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.compute(key, (k, current) -> (current == null ? 0.0 : current) - value);
    }

    CollectedMetric collect() {
        List<MetricSample> samples = new ArrayList<>();

        for (String key : values.keySet()) {
            Double value = values.remove(key);
            if (value != null) {
                samples.add(new GaugeMetricSample(CounterImpl.parseLabelKey(key), value));
            }
        }

        return new CollectedMetric(options.getName(), options.getHelp(), MetricType.GAUGE, samples);
    }
}
