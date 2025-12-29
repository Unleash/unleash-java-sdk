package io.getunleash.impactmetrics;

import io.getunleash.lang.Nullable;
import io.getunleash.variant.Variant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsAPI.class);

    private final ImpactMetricRegistry metricRegistry;
    private final VariantResolver variantResolver;
    private final StaticContext staticContext;

    public MetricsAPI(
            ImpactMetricRegistry metricRegistry,
            VariantResolver variantResolver,
            StaticContext staticContext) {
        this.metricRegistry = metricRegistry;
        this.variantResolver = variantResolver;
        this.staticContext = staticContext;
    }

    public void defineCounter(String name, String help) {
        if (name == null || name.isEmpty() || help == null || help.isEmpty()) {
            LOGGER.warn("Counter name or help cannot be empty: {}, {}", name, help);
            return;
        }
        List<String> labelNames = List.of("featureName", "appName", "environment");
        metricRegistry.counter(new MetricOptions(name, help, labelNames));
    }

    public void defineGauge(String name, String help) {
        if (name == null || name.isEmpty() || help == null || help.isEmpty()) {
            LOGGER.warn("Gauge name or help cannot be empty: {}, {}", name, help);
            return;
        }
        List<String> labelNames = List.of("featureName", "appName", "environment");
        metricRegistry.gauge(new MetricOptions(name, help, labelNames));
    }

    public void defineHistogram(String name, String help) {
        defineHistogram(name, help, null);
    }

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

    public void incrementCounter(String name) {
        incrementCounter(name, null, null);
    }

    public void incrementCounter(String name, long value) {
        incrementCounter(name, value, null);
    }

    public void incrementCounter(
            String name, @Nullable Long value, @Nullable MetricFlagContext flagContext) {
        Counter counter = metricRegistry.getCounter(name);
        if (counter == null) {
            LOGGER.warn("Counter {} not defined, this counter will not be incremented.", name);
            return;
        }

        Map<String, String> flagLabels = getFlagLabels(flagContext);
        Map<String, String> labels = new HashMap<>(flagLabels);
        labels.put("appName", staticContext.getAppName());
        labels.put("environment", staticContext.getEnvironment());

        counter.inc(value != null ? value : 1L, labels);
    }

    public void updateGauge(String name, long value) {
        updateGauge(name, value, null);
    }

    public void updateGauge(String name, long value, @Nullable MetricFlagContext flagContext) {
        Gauge gauge = metricRegistry.getGauge(name);
        if (gauge == null) {
            LOGGER.warn("Gauge {} not defined, this gauge will not be updated.", name);
            return;
        }

        Map<String, String> flagLabels = getFlagLabels(flagContext);
        Map<String, String> labels = new HashMap<>(flagLabels);
        labels.put("appName", staticContext.getAppName());
        labels.put("environment", staticContext.getEnvironment());

        gauge.set(value, labels);
    }

    public void observeHistogram(String name, double value) {
        observeHistogram(name, value, null);
    }

    public void observeHistogram(
            String name, double value, @Nullable MetricFlagContext flagContext) {
        Histogram histogram = metricRegistry.getHistogram(name);
        if (histogram == null) {
            LOGGER.warn("Histogram {} not defined, this histogram will not be updated.", name);
            return;
        }

        Map<String, String> flagLabels = getFlagLabels(flagContext);
        Map<String, String> labels = new HashMap<>(flagLabels);
        labels.put("appName", staticContext.getAppName());
        labels.put("environment", staticContext.getEnvironment());

        histogram.observe(value, labels);
    }
}
