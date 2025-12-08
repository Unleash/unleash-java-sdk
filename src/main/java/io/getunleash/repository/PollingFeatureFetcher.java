package io.getunleash.repository;

import io.getunleash.UnleashException;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.YggdrasilInvalidInputException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.UnleashReady;
import io.getunleash.streaming.FetchWorker;
import io.getunleash.util.Throttler;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PollingFeatureFetcher implements FetchWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingFeatureFetcher.class);

    private final UnleashConfig unleashConfig;
    private final EventDispatcher eventDispatcher;
    private final Throttler throttler;
    private final FeatureSource featureFetcher;
    private final UnleashEngine engine;
    private final BackupHandler featureBackupHandler;
    private boolean ready;

    PollingFeatureFetcher(
            UnleashConfig unleashConfig,
            EventDispatcher eventDispatcher,
            FeatureSource fetcher,
            UnleashEngine engine,
            BackupHandler featureBackupHandler) {
        this.unleashConfig = unleashConfig;
        this.eventDispatcher = eventDispatcher;
        this.featureFetcher = fetcher;
        this.engine = engine;
        this.featureBackupHandler = featureBackupHandler;
        this.throttler = initializeThrottler(unleashConfig);

        // don't love starting long running tasks in constructors
        // but you deal with the architecture you have not the one you want
        start();
    }

    private Throttler initializeThrottler(UnleashConfig config) {
        return new Throttler(
                (int) config.getFetchTogglesInterval(),
                300,
                config.getUnleashURLs().getFetchTogglesURL());
    }

    @Override
    public void start() {
        UnleashScheduledExecutor executor = unleashConfig.getScheduledExecutor();
        if (unleashConfig.isSynchronousFetchOnInitialisation()) {
            if (this.unleashConfig.getStartupExceptionHandler() != null) {
                updateFeatures(this.unleashConfig.getStartupExceptionHandler()).run();
            } else {
                updateFeatures(
                                // just throw exception handler
                                e -> {
                                    throw e;
                                })
                        .run();
            }
        }

        if (!unleashConfig.isDisablePolling()) {
            Runnable updateFeatures = updateFeatures(this.eventDispatcher::dispatch);
            if (unleashConfig.getFetchTogglesInterval() > 0) {
                executor.setInterval(updateFeatures, 0, unleashConfig.getFetchTogglesInterval());
            } else {
                executor.scheduleOnce(updateFeatures);
            }
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Runnable updateFeatures(final Consumer<UnleashException> handler) {
        return () -> {
            if (throttler.performAction()) {
                try {
                    ClientFeaturesResponse response = featureFetcher.fetchFeatures();
                    eventDispatcher.dispatch(response);
                    if (response.getStatus() == ClientFeaturesResponse.Status.CHANGED) {
                        String clientFeatures = response.getClientFeatures().get();

                        this.engine.takeState(clientFeatures);
                        this.featureBackupHandler.write(clientFeatures);
                    } else if (response.getStatus() == ClientFeaturesResponse.Status.UNAVAILABLE) {
                        if (!ready && unleashConfig.isSynchronousFetchOnInitialisation()) {
                            throw new UnleashException(
                                    String.format(
                                            "Could not initialize Unleash, got response code %d",
                                            response.getHttpStatusCode()),
                                    null);
                        }
                        if (ready) {
                            throttler.handleHttpErrorCodes(response.getHttpStatusCode());
                        }
                        return;
                    }
                    throttler.decrementFailureCountAndResetSkips();
                    if (!ready) {
                        eventDispatcher.dispatch(new UnleashReady());
                        ready = true;
                    }
                } catch (UnleashException e) {
                    handler.accept(e);
                } catch (YggdrasilInvalidInputException e) {
                    handler.accept(new UnleashException("Error when fetching features", e));
                }
            } else {
                throttler.skipped(); // We didn't do anything this iteration, just reduce the count
            }
        };
    }

    public Integer getFailures() {
        return this.throttler.getFailures();
    }

    public Integer getSkips() {
        return this.throttler.getSkips();
    }

    @Override
    public void stop() {
        LOGGER.warn("Attempting to stop polling but this currently isn't supported.");
        // Currently not supported but right now we don't need it
    }
}
