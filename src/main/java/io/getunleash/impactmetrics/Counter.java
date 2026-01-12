package io.getunleash.impactmetrics;

import java.util.Map;

interface Counter {
    void inc();

    void inc(long value);

    void inc(long value, Map<String, String> labels);
}
