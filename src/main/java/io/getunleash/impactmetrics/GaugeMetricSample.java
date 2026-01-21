package io.getunleash.impactmetrics;

import java.util.Map;
import java.util.Objects;

public class GaugeMetricSample implements MetricSample {
    private final Map<String, String> labels;
    private final double value;

    public GaugeMetricSample(Map<String, String> labels, double value) {
        this.labels = labels;
        this.value = value;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GaugeMetricSample that = (GaugeMetricSample) o;
        return Double.compare(that.value, value) == 0 && Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels, value);
    }

    @Override
    public String toString() {
        return "GaugeMetricSample{" + "labels=" + labels + ", value=" + value + '}';
    }
}
