package io.getunleash.impactmetrics;

import io.getunleash.UnleashContext;
import java.util.List;

public class MetricFlagContext {
    private final List<String> flagNames;
    private final UnleashContext context;

    public MetricFlagContext(List<String> flagNames, UnleashContext context) {
        this.flagNames = flagNames;
        this.context = context;
    }

    public List<String> getFlagNames() {
        return flagNames;
    }

    public UnleashContext getContext() {
        return context;
    }
}
