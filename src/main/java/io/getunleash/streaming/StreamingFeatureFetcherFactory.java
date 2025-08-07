package io.getunleash.streaming;

import io.getunleash.util.UnleashConfig;
import java.util.function.Consumer;

public class StreamingFeatureFetcherFactory {
    public static StreamingFeatureFetcher createStreamingFeatureFetcher(
            UnleashConfig config,
            Consumer<String> streamingUpdateHandler,
            Consumer<Throwable> streamingErrorHandler) {
        if (config.isStreamingMode()) {
            return new StreamingFeatureFetcherImpl(
                    config, streamingUpdateHandler, streamingErrorHandler);
        } else {
            return new NoOpStreamingFeatureFetcher();
        }
    }
}
