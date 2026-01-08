package io.getunleash.repository;

import io.getunleash.engine.UnleashEngine;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.GatedEventEmitter;
import io.getunleash.util.UnleashConfig;

public class AdaptiveFetcher implements FetchWorker, ModeController {

    interface WorkersProvider {
        Workers create(
                UnleashConfig config,
                BackupHandler backup,
                UnleashEngine engine,
                ModeController controller);
    }

    static final class Workers {
        final PollingFeatureFetcher polling;
        final StreamingFeatureFetcherImpl streaming;

        Workers(PollingFeatureFetcher polling, StreamingFeatureFetcherImpl streaming) {
            this.polling = polling;
            this.streaming = streaming;
        }
    }

    private static Workers defaultWorkers(
            UnleashConfig config,
            BackupHandler backup,
            UnleashEngine engine,
            ModeController controller) {
        GatedEventEmitter eventEmitter = new GatedEventEmitter(new EventDispatcher(config));

        PollingFeatureFetcher polling =
                new PollingFeatureFetcher(
                        config,
                        config.getUnleashFeatureFetcherFactory().apply(config),
                        engine,
                        backup,
                        eventEmitter);

        StreamingFeatureFetcherImpl streaming =
                new StreamingFeatureFetcherImpl(config, eventEmitter, engine, backup, controller);

        return new Workers(polling, streaming);
    }

    private final PollingFeatureFetcher pollingFetcher;
    private final StreamingFeatureFetcherImpl streamingFetcher;
    private final boolean startWithStreaming;

    public AdaptiveFetcher(UnleashConfig config, BackupHandler backup, UnleashEngine engine) {
        this(config, backup, engine, AdaptiveFetcher::defaultWorkers);
    }

    AdaptiveFetcher(
            UnleashConfig config,
            BackupHandler backup,
            UnleashEngine engine,
            WorkersProvider provider) {
        this.startWithStreaming = config.isStreamingMode();
        Workers workers = provider.create(config, backup, engine, this);

        this.pollingFetcher = workers.polling;
        this.streamingFetcher = workers.streaming;
    }

    @Override
    public void start() {
        if (startWithStreaming) {
            streamingFetcher.start();
        } else {
            pollingFetcher.start();
        }
    }

    @Override
    public void stop() {
        pollingFetcher.stop();
        streamingFetcher.stop();
    }

    @Override
    public void requestFailover() {
        streamingFetcher.stop();
        pollingFetcher.start();
    }
}
