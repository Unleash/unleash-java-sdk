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
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureRepositoryImpl implements FeatureRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureRepositoryImpl.class);
    private final BackupHandler featureBackupHandler;
    private final ToggleBootstrapProvider bootstrapper;
    private final FetchWorker fetcher;
    private final GatedEventEmitter eventDispatcher;
    private final UnleashEngine engine;

    public FeatureRepositoryImpl(UnleashConfig unleashConfig, UnleashEngine engine) {
        this(unleashConfig, new FeatureBackupHandlerFile(unleashConfig), engine);
    }

    FeatureRepositoryImpl(
            UnleashConfig unleashConfig, BackupHandler featureBackupHandler, UnleashEngine engine) {

        GatedEventEmitter readyOnceGate = new GatedEventEmitter(new EventDispatcher(unleashConfig));

        this.featureBackupHandler = featureBackupHandler;
        this.engine = engine;
        this.eventDispatcher = readyOnceGate;
        this.fetcher = new AdaptiveFetcher(unleashConfig, featureBackupHandler, engine);
        this.bootstrapper = unleashConfig.getToggleBootstrapProvider();
        this.initCollections(unleashConfig.getScheduledExecutor());
    }

    FeatureRepositoryImpl(
            UnleashConfig unleashConfig,
            BackupHandler featureBackupHandler,
            UnleashEngine engine,
            FetchWorker fetcher,
            ToggleBootstrapProvider bootstrapHandler,
            EventDispatcher eventDispatcher) {
        GatedEventEmitter readyOnceGate = new GatedEventEmitter(eventDispatcher);

        this.featureBackupHandler = featureBackupHandler;
        this.engine = engine;
        this.bootstrapper = bootstrapHandler;
        this.eventDispatcher = readyOnceGate;
        this.fetcher = fetcher;
        this.initCollections(unleashConfig.getScheduledExecutor());
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
        fetcher.start();
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
        this.fetcher.stop();
    }
}
