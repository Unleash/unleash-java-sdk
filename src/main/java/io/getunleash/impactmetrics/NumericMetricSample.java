package io.getunleash.impactmetrics;

import java.util.Map;
import java.util.Objects;

public class NumericMetricSample implements MetricSample {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumericMetricSample that = (NumericMetricSample) o;
        return value == that.value && Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels, value);
    }

    @Override
    public String toString() {
        return "NumericMetricSample{" + "labels=" + labels + ", value=" + value + '}';
    }
}
