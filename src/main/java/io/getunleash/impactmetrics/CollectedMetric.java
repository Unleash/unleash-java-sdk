package io.getunleash.impactmetrics;

import java.util.List;

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
}
