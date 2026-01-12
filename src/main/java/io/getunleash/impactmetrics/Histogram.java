package io.getunleash.impactmetrics;

import java.util.Map;

interface Histogram {
    void observe(double value);

    void observe(double value, Map<String, String> labels);

    void restore(BucketMetricSample sample);
}
