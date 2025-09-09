package io.getunleash.metric;

import io.getunleash.engine.MetricsBucket;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.util.Throttler;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnleashMetricServiceImpl implements UnleashMetricService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnleashMetricServiceImpl.class);
    private final LocalDateTime started;
    private final UnleashConfig unleashConfig;
    private final MetricSender metricSender;

    // synchronization is handled in the engine itself
    private final UnleashEngine engine;

    private final Throttler throttler;

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

                ClientMetrics metrics = new ClientMetrics(unleashConfig, bucket);
                int statusCode = metricSender.sendMetrics(metrics);
                if (statusCode >= 200 && statusCode < 400) {
                    throttler.decrementFailureCountAndResetSkips();
                }
                if (statusCode >= 400) {
                    throttler.handleHttpErrorCodes(statusCode);
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
