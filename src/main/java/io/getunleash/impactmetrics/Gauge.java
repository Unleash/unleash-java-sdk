package io.getunleash.impactmetrics;

import java.util.Map;

interface Gauge {
    void set(double value);

    void set(double value, Map<String, String> labels);

    void inc();

    void inc(double value);

    void inc(double value, Map<String, String> labels);

    void dec();

    void dec(double value);

    void dec(double value, Map<String, String> labels);
}
