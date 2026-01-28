package io.getunleash.impactmetrics;

import io.getunleash.lang.Nullable;
import io.getunleash.variant.Variant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsAPIImpl implements MetricsAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsAPIImpl.class);

    private final ImpactMetricRegistry metricRegistry;
    private final VariantResolver variantResolver;
    private final ImpactMetricContext context;

    public MetricsAPIImpl(
            ImpactMetricRegistry metricRegistry,
            VariantResolver variantResolver,
            ImpactMetricContext context) {
        this.metricRegistry = metricRegistry;
        this.variantResolver = variantResolver;
        this.context = context;
    }

    @Override
    public void defineCounter(String name, String help) {
        if (name == null || name.isEmpty() || help == null || help.isEmpty()) {
            LOGGER.warn("Counter name or help cannot be empty: {}, {}", name, help);
            return;
        }
        List<String> labelNames = List.of("featureName", "appName", "environment");
        metricRegistry.counter(new MetricOptions(name, help, labelNames));
    }

    @Override
    public void defineGauge(String name, String help) {
        if (name == null || name.isEmpty() || help == null || help.isEmpty()) {
            LOGGER.warn("Gauge name or help cannot be empty: {}, {}", name, help);
            return;
        }
        List<String> labelNames = List.of("featureName", "appName", "environment");
        metricRegistry.gauge(new MetricOptions(name, help, labelNames));
    }

    @Override
    public void defineHistogram(String name, String help) {
        defineHistogram(name, help, null);
    }

    @Override
    public void defineHistogram(String name, String help, @Nullable List<Double> buckets) {
        if (name == null || name.isEmpty() || help == null || help.isEmpty()) {
            LOGGER.warn("Histogram name or help cannot be empty: {}, {}", name, help);
            return;
        }
        List<String> labelNames = List.of("featureName", "appName", "environment");
        List<Double> bucketList = buckets != null ? buckets : new ArrayList<>();
        metricRegistry.histogram(new BucketMetricOptions(name, help, labelNames, bucketList));
    }

    private Map<String, String> getFlagLabels(@Nullable MetricFlagContext flagContext) {
        Map<String, String> flagLabels = new HashMap<>();
        if (flagContext != null) {
            for (String flag : flagContext.getFlagNames()) {
                Variant variant =
                        variantResolver.getVariantForImpactMetrics(flag, flagContext.getContext());

                if (variant.isEnabled()) {
                    flagLabels.put(flag, variant.getName());
                } else if (variant.isFeatureEnabled()) {
                    flagLabels.put(flag, "enabled");
                } else {
                    flagLabels.put(flag, "disabled");
                }
            }
        }
        return flagLabels;
    }

    @Override
    public void incrementCounter(String name) {
        incrementCounter(name, null, null);
    }

    @Override
    public void incrementCounter(String name, long value) {
        incrementCounter(name, value, null);
    }

    @Deprecated(since = "12.1.1", forRemoval = true)
    @Override
    public void incrementCounter(
            String name, @Nullable Long value, @Nullable MetricFlagContext flagContext) {
        Counter counter = metricRegistry.getCounter(name);
        if (counter == null) {
            LOGGER.warn("Counter {} not defined, this counter will not be incremented.", name);
            return;
        }

        Map<String, String> flagLabels = getFlagLabels(flagContext);
        Map<String, String> labels = new HashMap<>(flagLabels);
        labels.put("appName", context.getAppName());
        labels.put("environment", context.getEnvironment());

        counter.inc(value != null ? value : 1L, labels);
    }

    @Override
    public void updateGauge(String name, double value) {
        updateGauge(name, value, null);
    }

    @Deprecated(since = "12.1.1", forRemoval = true)
    @Override
    public void updateGauge(String name, double value, @Nullable MetricFlagContext flagContext) {
        Gauge gauge = metricRegistry.getGauge(name);
        if (gauge == null) {
            LOGGER.warn("Gauge {} not defined, this gauge will not be updated.", name);
            return;
        }

        Map<String, String> flagLabels = getFlagLabels(flagContext);
        Map<String, String> labels = new HashMap<>(flagLabels);
        labels.put("appName", context.getAppName());
        labels.put("environment", context.getEnvironment());

        gauge.set(value, labels);
    }

    @Deprecated(since = "12.1.0", forRemoval = true)
    @Override
    public void updateGauge(String name, long value) {
        updateGauge(name, (double) value);
    }

    @Deprecated(since = "12.1.0", forRemoval = true)
    @Override
    public void updateGauge(String name, long value, @Nullable MetricFlagContext flagContext) {
        updateGauge(name, (double) value, flagContext);
    }

    @Override
    public void observeHistogram(String name, double value) {
        observeHistogram(name, value, null);
    }

    @Deprecated(since = "12.1.1", forRemoval = true)
    @Override
    public void observeHistogram(
            String name, double value, @Nullable MetricFlagContext flagContext) {
        Histogram histogram = metricRegistry.getHistogram(name);
        if (histogram == null) {
            LOGGER.warn("Histogram {} not defined, this histogram will not be updated.", name);
            return;
        }

        Map<String, String> flagLabels = getFlagLabels(flagContext);
        Map<String, String> labels = new HashMap<>(flagLabels);
        labels.put("appName", context.getAppName());
        labels.put("environment", context.getEnvironment());

        histogram.observe(value, labels);
    }
}
