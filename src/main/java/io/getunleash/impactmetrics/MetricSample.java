package io.getunleash.impactmetrics;

import java.util.Map;

interface MetricSample {
    Map<String, String> getLabels();
}
