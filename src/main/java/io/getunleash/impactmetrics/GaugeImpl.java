package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class GaugeImpl implements Gauge {
    private final MetricOptions options;
    private final ConcurrentHashMap<String, Long> values = new ConcurrentHashMap<>();

    GaugeImpl(MetricOptions options) {
        this.options = options;
    }

    @Override
    public void set(long value) {
        set(value, null);
    }

    @Override
    public void set(long value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.put(key, value);
    }

    @Override
    public void inc() {
        inc(1, null);
    }

    @Override
    public void inc(long value) {
        inc(value, null);
    }

    @Override
    public void inc(long value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.compute(key, (k, current) -> (current == null ? 0L : current) + value);
    }

    @Override
    public void dec() {
        dec(1, null);
    }

    @Override
    public void dec(long value) {
        dec(value, null);
    }

    @Override
    public void dec(long value, Map<String, String> labels) {
        String key = CounterImpl.getLabelKey(labels);
        values.compute(key, (k, current) -> (current == null ? 0L : current) - value);
    }

    public CollectedMetric collect() {
        List<NumericMetricSample> samples = new ArrayList<>();

        for (String key : values.keySet()) {
            Long value = values.remove(key);
            if (value != null) {
                samples.add(new NumericMetricSample(CounterImpl.parseLabelKey(key), value));
            }
        }

        return new CollectedMetric(options.getName(), options.getHelp(), MetricType.GAUGE, samples);
    }
}
