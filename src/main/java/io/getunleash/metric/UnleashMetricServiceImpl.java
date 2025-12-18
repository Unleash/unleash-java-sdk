package io.getunleash.metric;

import io.getunleash.engine.MetricsBucket;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.impactmetrics.CollectedMetric;
import io.getunleash.impactmetrics.ImpactMetricsDataSource;
import io.getunleash.impactmetrics.InMemoryMetricRegistry;
import io.getunleash.util.Throttler;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public class UnleashMetricServiceImpl implements UnleashMetricService {
    private final LocalDateTime started;
    private final UnleashConfig unleashConfig;
    private final MetricSender metricSender;

    // synchronization is handled in the engine itself
    private final UnleashEngine engine;

    private final Throttler throttler;

    private final ImpactMetricsDataSource impactMetricsRegistry;

    public UnleashMetricServiceImpl(
            UnleashConfig unleashConfig, UnleashScheduledExecutor executor, UnleashEngine engine) {
        this(
                unleashConfig,
                unleashConfig.getMetricSenderFactory().apply(unleashConfig),
                executor,
                engine);
    }

    public UnleashMetricServiceImpl(
            UnleashConfig unleashConfig,
            MetricSender metricSender,
            UnleashScheduledExecutor executor,
            UnleashEngine engine) {
        this.started = LocalDateTime.now(ZoneId.of("UTC"));
        this.unleashConfig = unleashConfig;
        this.metricSender = metricSender;
        this.throttler =
                new Throttler(
                        (int) unleashConfig.getSendMetricsInterval(),
                        300,
                        unleashConfig.getUnleashURLs().getClientMetricsURL());
        this.engine = engine;
        ImpactMetricsDataSource configuredRegistry = unleashConfig.getImpactMetricsRegistry();
        this.impactMetricsRegistry =
                configuredRegistry != null ? configuredRegistry : new InMemoryMetricRegistry();
        long metricsInterval = unleashConfig.getSendMetricsInterval();

        executor.setInterval(sendMetrics(), metricsInterval, metricsInterval);
    }

    @Override
    public void register(Set<String> strategies) {
        ClientRegistration registration =
                new ClientRegistration(unleashConfig, started, strategies);
        metricSender.registerClient(registration);
    }

    private Runnable sendMetrics() {
        return () -> {
            if (throttler.performAction()) {
                MetricsBucket bucket = this.engine.getMetrics();

                List<CollectedMetric> impactMetrics = impactMetricsRegistry.collect();
                List<CollectedMetric> impactMetricsOrNull =
                        impactMetrics.isEmpty() ? null : impactMetrics;

                ClientMetrics metrics =
                        new ClientMetrics(unleashConfig, bucket, impactMetricsOrNull);
                int statusCode = metricSender.sendMetrics(metrics);
                if (statusCode >= 200 && statusCode < 400) {
                    throttler.decrementFailureCountAndResetSkips();
                }
                if (statusCode >= 400) {
                    throttler.handleHttpErrorCodes(statusCode);
                    if (impactMetricsOrNull != null) {
                        impactMetricsRegistry.restore(impactMetricsOrNull);
                    }
                }

            } else {
                throttler.skipped();
            }
        };
    }

    protected int getSkips() {
        return this.throttler.getSkips();
    }

    protected int getFailures() {
        return this.throttler.getFailures();
    }
}
