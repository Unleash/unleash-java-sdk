package io.getunleash.metric;

import java.util.Set;

public interface UnleashMetricService {
    void register(Set<String> strategies);
}
