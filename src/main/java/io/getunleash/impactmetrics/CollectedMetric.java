package io.getunleash.impactmetrics;

import java.util.List;
import java.util.Objects;

public class CollectedMetric {
    private final String name;
    private final String help;
    private final MetricType type;
    private final List<NumericMetricSample> samples;

    public CollectedMetric(
            String name, String help, MetricType type, List<NumericMetricSample> samples) {
        this.name = name;
        this.help = help;
        this.type = type;
        this.samples = samples;
    }

    public String getName() {
        return name;
    }

    public String getHelp() {
        return help;
    }

    public MetricType getType() {
        return type;
    }

    public List<NumericMetricSample> getSamples() {
        return samples;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectedMetric that = (CollectedMetric) o;
        return Objects.equals(name, that.name)
                && Objects.equals(help, that.help)
                && type == that.type
                && Objects.equals(samples, that.samples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, help, type, samples);
    }

    @Override
    public String toString() {
        return "CollectedMetric{"
                + "name='"
                + name
                + '\''
                + ", help='"
                + help
                + '\''
                + ", type="
                + type
                + ", samples="
                + samples
                + '}';
    }
}
