package io.getunleash.impactmetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class CounterImpl implements Counter {
    private final MetricOptions options;
    private final ConcurrentHashMap<String, Long> values = new ConcurrentHashMap<>();

    CounterImpl(MetricOptions options) {
        this.options = options;
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
        String key = getLabelKey(labels);
        values.compute(key, (k, current) -> (current == null ? 0L : current) + value);
    }

    CollectedMetric collect() {
        List<MetricSample> samples = new ArrayList<>();

        for (String key : values.keySet()) {
            Long value = values.remove(key);
            if (value != null) {
                samples.add(new NumericMetricSample(parseLabelKey(key), value));
            }
        }

        if (samples.isEmpty()) {
            samples.add(new NumericMetricSample(Collections.emptyMap(), 0L));
        }

        return new CollectedMetric(
                options.getName(), options.getHelp(), MetricType.COUNTER, samples);
    }

    static String getLabelKey(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        return labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    static Map<String, String> parseLabelKey(String key) {
        if (key == null || key.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> labels = new HashMap<>();
        for (String pair : key.split(",")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                labels.put(parts[0], parts[1]);
            }
        }
        return labels;
    }
}
