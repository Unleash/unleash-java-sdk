package io.getunleash.impactmetrics;

import io.getunleash.UnleashContext;
import java.util.List;

public class MetricFlagContext {
    private final List<String> flagNames;
    private final UnleashContext context;

    MetricFlagContext(List<String> flagNames, UnleashContext context) {
        this.flagNames = flagNames;
        this.context = context;
    }

    List<String> getFlagNames() {
        return flagNames;
    }

    UnleashContext getContext() {
        return context;
    }
}
