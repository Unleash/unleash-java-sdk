package io.getunleash.impactmetrics;

import java.util.Map;

public class NumericMetricSample {
    private final Map<String, String> labels;
    private final long value;

    public NumericMetricSample(Map<String, String> labels, long value) {
        this.labels = labels;
        this.value = value;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public long getValue() {
        return value;
    }
}
