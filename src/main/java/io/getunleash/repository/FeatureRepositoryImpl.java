package io.getunleash.repository;

import io.getunleash.FeatureDefinition;
import io.getunleash.UnleashContext;
import io.getunleash.UnleashException;
import io.getunleash.engine.FlatResponse;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.VariantDef;
import io.getunleash.engine.YggdrasilInvalidInputException;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.GatedEventEmitter;
import io.getunleash.streaming.StreamingFeatureFetcherImpl;
import io.getunleash.util.Throttler;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureRepositoryImpl implements FeatureRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureRepositoryImpl.class);
    private final UnleashConfig unleashConfig;
    private final BackupHandler featureBackupHandler;
    private final ToggleBootstrapProvider bootstrapper;
    private final FetchWorker streamingFeatureFetcher;
    private final FetchWorker pollingFeatureFetcher;
    private final GatedEventEmitter eventDispatcher;
    private final UnleashEngine engine;
    private final Throttler throttler;

    public FeatureRepositoryImpl(UnleashConfig unleashConfig, UnleashEngine engine) {
        this(unleashConfig, new FeatureBackupHandlerFile(unleashConfig), engine);
    }

    FeatureRepositoryImpl(
            UnleashConfig unleashConfig, BackupHandler featureBackupHandler, UnleashEngine engine) {

        GatedEventEmitter readyOnceGate = new GatedEventEmitter(new EventDispatcher(unleashConfig));

        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = featureBackupHandler;
        this.engine = engine;
        this.eventDispatcher = readyOnceGate;
        this.pollingFeatureFetcher =
                new PollingFeatureFetcher(
                        unleashConfig,
                        unleashConfig.getUnleashFeatureFetcherFactory().apply(unleashConfig),
                        engine,
                        featureBackupHandler,
                        readyOnceGate);
        this.streamingFeatureFetcher =
                new StreamingFeatureFetcherImpl(
                        unleashConfig,
                        new EventDispatcher(unleashConfig),
                        engine,
                        featureBackupHandler);
        this.bootstrapper = unleashConfig.getToggleBootstrapProvider();
        this.throttler = initializeThrottler(unleashConfig);
        this.initCollections(unleashConfig.getScheduledExecutor());
    }

    FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FeatureFetcher fetcher,
            FetchWorker streamingFeatureFetcher) {
        this(
                unleashConfig,
                featureBackupHandler,
                engine,
                fetcher,
                streamingFeatureFetcher,
                unleashConfig.getToggleBootstrapProvider());
    }

    FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FeatureFetcher fetcher,
            FetchWorker streamingFeatureFetcher,
            ToggleBootstrapProvider bootstrapHandler) {
        this(
                unleashConfig,
                featureBackupHandler,
                engine,
                fetcher,
                streamingFeatureFetcher,
                bootstrapHandler,
                new EventDispatcher(unleashConfig));
    }

    FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FeatureFetcher fetcher,
            FetchWorker streamingFeatureFetcher,
            ToggleBootstrapProvider bootstrapHandler,
            EventDispatcher eventDispatcher) {
        GatedEventEmitter readyOnceGate = new GatedEventEmitter(eventDispatcher);

        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = featureBackupHandler;
        this.engine = engine;
        this.pollingFeatureFetcher =
                new PollingFeatureFetcher(
                        unleashConfig, fetcher, engine, featureBackupHandler, readyOnceGate);
        this.bootstrapper = unleashConfig.getToggleBootstrapProvider();
        this.eventDispatcher = readyOnceGate;
        this.throttler = initializeThrottler(unleashConfig);
        this.streamingFeatureFetcher = streamingFeatureFetcher;
        this.initCollections(unleashConfig.getScheduledExecutor());
    }

    private Throttler initializeThrottler(UnleashConfig config) {
        return new Throttler(
                (int) config.getFetchTogglesInterval(),
                300,
                config.getUnleashURLs().getFetchTogglesURL());
    }

    private void initCollections(UnleashScheduledExecutor executor) {
        Optional<String> features = this.featureBackupHandler.read();
        if (!features.isPresent() && this.bootstrapper != null) {
            features = this.bootstrapper.read();
        }
        if (features.isPresent()) {
            try {
                this.engine.takeState(features.get());
            } catch (YggdrasilInvalidInputException e) {
                LOGGER.error("Error when initializing feature toggles", e);
                eventDispatcher.error(new UnleashException("Failed to read backup file:", e));
            }
        }

        if (unleashConfig.isStreamingMode()) {
            streamingFeatureFetcher.start();
        } else {
            pollingFeatureFetcher.start();
        }
    }

    public Integer getFailures() {
        return this.throttler.getFailures();
    }

    public Integer getSkips() {
        return this.throttler.getSkips();
    }

    @Override
    public FlatResponse<Boolean> isEnabled(String toggleName, UnleashContext context) {
        try {
            return this.engine.isEnabled(toggleName, YggdrasilAdapters.adapt(context));
        } catch (YggdrasilInvalidInputException e) {
            LOGGER.error("Error when checking feature toggle {}", toggleName, e);
            return null;
        }
    }

    @Override
    public FlatResponse<VariantDef> getVariant(String toggleName, UnleashContext context) {
        try {
            return this.engine.getVariant(toggleName, YggdrasilAdapters.adapt(context));
        } catch (YggdrasilInvalidInputException e) {
            LOGGER.error("Error when checking feature toggle {}", toggleName, e);
            return null;
        }
    }

    @Override
    public Stream<FeatureDefinition> listKnownToggles() {
        return this.engine.listKnownToggles().stream().map(FeatureDefinition::new);
    }

    @Override
    public void shutdown() {
        this.streamingFeatureFetcher.stop();
    }
}
