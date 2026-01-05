package io.getunleash.repository;

import io.getunleash.UnleashException;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.YggdrasilInvalidInputException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.GatedEventEmitter;
import io.getunleash.util.Throttler;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PollingFeatureFetcher implements FetchWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingFeatureFetcher.class);

    private final UnleashConfig unleashConfig;
    private final Throttler throttler;
    private final FeatureFetcher featureFetcher;
    private final UnleashEngine engine;
    private final BackupHandler featureBackupHandler;
    private final GatedEventEmitter eventEmitter;

    PollingFeatureFetcher(
            UnleashConfig unleashConfig,
            FeatureFetcher fetcher,
            UnleashEngine engine,
            BackupHandler featureBackupHandler,
            GatedEventEmitter readyOnceGate) {
        this.unleashConfig = unleashConfig;
        this.featureFetcher = fetcher;
        this.engine = engine;
        this.featureBackupHandler = featureBackupHandler;
        this.eventEmitter = readyOnceGate;
        this.throttler = initializeThrottler(unleashConfig);
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
                runInitialFetch(this.unleashConfig.getStartupExceptionHandler()).run();
            } else {
                runInitialFetch(
                                // just throw exception handler
                                e -> {
                                    throw e;
                                })
                        .run();
            }
        }

        if (!unleashConfig.isDisablePolling()) {
            Runnable initialFetch = runInitialFetch(this.eventEmitter::error);
            executor.scheduleOnce(initialFetch);

            if (unleashConfig.getFetchTogglesInterval() > 0) {
                Runnable updateFeatures = runSteadyStateFetch(this.eventEmitter::error);
                executor.setInterval(
                        updateFeatures,
                        unleashConfig.getFetchTogglesInterval(),
                        unleashConfig.getFetchTogglesInterval());
            }
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Runnable runInitialFetch(final Consumer<UnleashException> handler) {
        return () -> {
            try {
                ClientFeaturesResponse response = featureFetcher.fetchFeatures();
                eventEmitter.update(response);
                if (response.getStatus() == ClientFeaturesResponse.Status.CHANGED) {
                    updateFeatures(response);
                } else if (response.getStatus() == ClientFeaturesResponse.Status.UNAVAILABLE) {
                    if (unleashConfig.isSynchronousFetchOnInitialisation()) {
                        throw new UnleashException(
                                String.format(
                                        "Could not initialize Unleash, got response code %d",
                                        response.getHttpStatusCode()),
                                null);
                    }
                }
            } catch (UnleashException e) {
                handler.accept(e);
            } catch (YggdrasilInvalidInputException e) {
                handler.accept(new UnleashException("Error on initial fetch", e));
            }
        };
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Runnable runSteadyStateFetch(final Consumer<UnleashException> handler) {
        return () -> {
            if (throttler.performAction()) {
                try {
                    ClientFeaturesResponse response = featureFetcher.fetchFeatures();
                    eventEmitter.update(response);
                    if (response.getStatus() == ClientFeaturesResponse.Status.CHANGED) {
                        updateFeatures(response);
                    } else if (response.getStatus() == ClientFeaturesResponse.Status.UNAVAILABLE) {
                        throttler.handleHttpErrorCodes(response.getHttpStatusCode());
                        return;
                    }
                    throttler.decrementFailureCountAndResetSkips();
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

    private void updateFeatures(ClientFeaturesResponse response)
            throws YggdrasilInvalidInputException {
        String clientFeatures = response.getClientFeatures().get();
        this.engine.takeState(clientFeatures);
        this.featureBackupHandler.write(clientFeatures);
        eventEmitter.ready();
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
