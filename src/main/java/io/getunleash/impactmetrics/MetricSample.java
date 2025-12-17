package io.getunleash.impactmetrics;

import java.util.Map;

public interface MetricSample {
    Map<String, String> getLabels();
}
