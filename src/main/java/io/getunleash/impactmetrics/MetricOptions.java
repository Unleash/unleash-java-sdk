package io.getunleash.impactmetrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MetricOptions {
    private final String name;
    private final String help;
    private final List<String> labelNames;

    public MetricOptions(String name, String help) {
        this(name, help, Collections.emptyList());
    }

    public MetricOptions(String name, String help, List<String> labelNames) {
        this.name = name;
        this.help = help;
        this.labelNames = labelNames;
    }

    public String getName() {
        return name;
    }

    public String getHelp() {
        return help;
    }

    public List<String> getLabelNames() {
        return labelNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricOptions that = (MetricOptions) o;
        return Objects.equals(name, that.name)
                && Objects.equals(help, that.help)
                && Objects.equals(labelNames, that.labelNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, help, labelNames);
    }
}
