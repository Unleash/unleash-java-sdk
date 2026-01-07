package io.getunleash.repository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.getunleash.engine.UnleashEngine;
import io.getunleash.util.UnleashConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveFetcherTest {

    final BackupHandler backupHandler = mock(BackupHandler.class);
    final UnleashEngine unleashEngine = mock(UnleashEngine.class);
    PollingFeatureFetcher pollingFeatureFetcher;
    StreamingFeatureFetcherImpl streamingFeatureFetcher;
    AdaptiveFetcher.WorkersProvider workersProvider;
    UnleashConfig.Builder configBuilder;

    @BeforeEach
    public void setUp() {
        pollingFeatureFetcher = mock(PollingFeatureFetcher.class);
        streamingFeatureFetcher = mock(StreamingFeatureFetcherImpl.class);
        workersProvider =
                (cfg, backup, engine, controller) ->
                        new AdaptiveFetcher.Workers(pollingFeatureFetcher, streamingFeatureFetcher);

        configBuilder =
                UnleashConfig.builder()
                        .appName("adaptive-fetcher-test")
                        .unleashAPI("https://example.com/api");
    }

    @Test
    void startingAdaptiveFetcherAlsoStartsStreamingChild() {
        UnleashConfig config = configBuilder.experimentalStreamingMode().build();

        AdaptiveFetcher adaptiveFetcher =
                new AdaptiveFetcher(config, backupHandler, unleashEngine, workersProvider);

        adaptiveFetcher.start();

        verify(streamingFeatureFetcher).start();
        verify(pollingFeatureFetcher, never()).start();
    }

    @Test
    void defaultsToPollingIfNotSpecified() {
        UnleashConfig config = configBuilder.build();

        AdaptiveFetcher adaptiveFetcher =
                new AdaptiveFetcher(config, backupHandler, unleashEngine, workersProvider);

        adaptiveFetcher.start();

        verify(pollingFeatureFetcher).start();
        verify(streamingFeatureFetcher, never()).start();
    }

    @Test
    void failoverOccursWhenRequested() {
        UnleashConfig config = configBuilder.experimentalStreamingMode().build();

        AdaptiveFetcher adaptiveFetcher =
                new AdaptiveFetcher(config, backupHandler, unleashEngine, workersProvider);

        adaptiveFetcher.start();

        verify(streamingFeatureFetcher).start();
        verify(pollingFeatureFetcher, never()).start();

        adaptiveFetcher.requestFailover();

        verify(streamingFeatureFetcher).stop();
        verify(pollingFeatureFetcher).start();
    }
}
