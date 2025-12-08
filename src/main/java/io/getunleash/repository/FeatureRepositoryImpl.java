package io.getunleash.repository;

import io.getunleash.FeatureDefinition;
import io.getunleash.UnleashContext;
import io.getunleash.UnleashException;
import io.getunleash.engine.FlatResponse;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.VariantDef;
import io.getunleash.engine.YggdrasilInvalidInputException;
import io.getunleash.event.EventDispatcher;
import io.getunleash.streaming.FetchWorker;
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
    private final FeatureSource featureFetcher;
    private final FetchWorker streamingFeatureFetcher;
    private final FetchWorker pollingFeatureFetcher;
    private final EventDispatcher eventDispatcher;
    private final UnleashEngine engine;
    private final Throttler throttler;

    public FeatureRepositoryImpl(UnleashConfig unleashConfig, UnleashEngine engine) {
        this(unleashConfig, new FeatureBackupHandlerFile(unleashConfig), engine);
    }

    public FeatureRepositoryImpl(
            UnleashConfig unleashConfig, BackupHandler featureBackupHandler, UnleashEngine engine) {
        this(unleashConfig, featureBackupHandler, engine, new EventDispatcher(unleashConfig));
    }

    private FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            EventDispatcher eventDispatcher) {
        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = featureBackupHandler;
        this.engine = engine;
        this.featureFetcher = unleashConfig.getUnleashFeatureFetcherFactory().apply(unleashConfig);
        this.bootstrapper = unleashConfig.getToggleBootstrapProvider();
        this.eventDispatcher = eventDispatcher;
        this.throttler = initializeThrottler(unleashConfig);
        this.streamingFeatureFetcher = new StreamingFeatureFetcherImpl(unleashConfig, eventDispatcher, engine, featureBackupHandler);
        this.pollingFeatureFetcher = new PollingFeatureFetcher(
                unleashConfig,
                eventDispatcher,
                featureFetcher,
                engine,
                featureBackupHandler);

        this.initCollections(unleashConfig.getScheduledExecutor());
    }

    public FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FeatureSource fetcher,
            FetchWorker streamingFeatureFetcher,
            FetchWorker pollingFeatureFetcher) {
        this(
                unleashConfig,
                featureBackupHandler,
                engine,
                fetcher,
                streamingFeatureFetcher,
                pollingFeatureFetcher,
                unleashConfig.getToggleBootstrapProvider());
    }

    public FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FeatureSource fetcher,
            FetchWorker streamingFeatureFetcher,
            FetchWorker pollingFeatureFetcher,
            ToggleBootstrapProvider bootstrapHandler) {
        this(
                unleashConfig,
                featureBackupHandler,
                engine,
                fetcher,
                streamingFeatureFetcher,
                pollingFeatureFetcher,
                bootstrapHandler,
                new EventDispatcher(unleashConfig));
    }

    public FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FeatureSource fetcher,
            FetchWorker streamingFeatureFetcher,
            FetchWorker pollingFeatureFetcher,
            ToggleBootstrapProvider bootstrapHandler,
            EventDispatcher eventDispatcher) {
        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = featureBackupHandler;
        this.engine = engine;
        this.featureFetcher = fetcher;
        this.streamingFeatureFetcher = streamingFeatureFetcher;
        this.pollingFeatureFetcher = pollingFeatureFetcher;
        this.bootstrapper = bootstrapHandler;
        this.eventDispatcher = eventDispatcher;
        this.throttler = initializeThrottler(unleashConfig);
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
                eventDispatcher.dispatch(new UnleashException("Failed to read backup file:", e));
            }
        }

        if (!unleashConfig.isStreamingMode()) {
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
