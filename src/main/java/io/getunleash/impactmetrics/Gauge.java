package io.getunleash.impactmetrics;

import java.util.Map;

public interface Gauge {
    void set(long value);

    void set(long value, Map<String, String> labels);

    void inc();

    void inc(long value);

    void inc(long value, Map<String, String> labels);

    void dec();

    void dec(long value);

    void dec(long value, Map<String, String> labels);
}
