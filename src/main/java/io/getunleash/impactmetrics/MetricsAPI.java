package io.getunleash.impactmetrics;

import io.getunleash.lang.Nullable;
import java.util.List;

public interface MetricsAPI {

    public void defineCounter(String name, String help);

    public void defineGauge(String name, String help);

    public void defineHistogram(String name, String help);

    public void defineHistogram(String name, String help, @Nullable List<Double> buckets);

    public void incrementCounter(String name);

    public void incrementCounter(String name, long value);

    public void incrementCounter(
            String name, @Nullable Long value, @Nullable MetricFlagContext flagContext);

    public void updateGauge(String name, double value);

    public void updateGauge(String name, double value, @Nullable MetricFlagContext flagContext);

    @Deprecated(since = "12.1.0", forRemoval = true)
    public void updateGauge(String name, long value);

    @Deprecated(since = "12.1.0", forRemoval = true)
    public void updateGauge(String name, long value, @Nullable MetricFlagContext flagContext);

    public void observeHistogram(String name, double value);

    public void observeHistogram(
            String name, double value, @Nullable MetricFlagContext flagContext);
}
