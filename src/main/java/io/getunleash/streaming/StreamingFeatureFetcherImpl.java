package io.getunleash.streaming;

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import io.getunleash.util.UnleashConfig;
import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamingFeatureFetcherImpl implements StreamingFeatureFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingFeatureFetcherImpl.class);

    private final UnleashConfig config;
    private final Consumer<String> streamingUpdateHandler;
    private final Consumer<Throwable> streamingErrorHandler;

    private volatile BackgroundEventSource eventSource;

    public StreamingFeatureFetcherImpl(
            UnleashConfig config,
            Consumer<String> streamingUpdateHandler,
            Consumer<Throwable> streamingErrorHandler) {
        this.config = config;
        this.streamingUpdateHandler = streamingUpdateHandler;
        this.streamingErrorHandler = streamingErrorHandler;
    }

    public void start() {
        try {
            URI streamingUri = config.getUnleashURLs().getStreamingURL().toURI();

            Headers.Builder headersBuilder = new Headers.Builder();
            config.getCustomHttpHeaders().forEach(headersBuilder::add);
            config.getCustomHttpHeadersProvider().getCustomHeaders().forEach(headersBuilder::add);

            headersBuilder.add(UnleashConfig.UNLEASH_APP_NAME_HEADER, config.getAppName());
            headersBuilder.add(UnleashConfig.UNLEASH_INSTANCE_ID_HEADER, config.getInstanceId());
            headersBuilder.add(
                    UnleashConfig.UNLEASH_CONNECTION_ID_HEADER, config.getConnectionId());
            headersBuilder.add(UnleashConfig.UNLEASH_SDK_HEADER, config.getSdkVersion());
            headersBuilder.add("Unleash-Client-Spec", config.getClientSpecificationVersion());

            OkHttpClient httpClient =
                    new OkHttpClient.Builder()
                            .readTimeout(Duration.ofSeconds(60)) // Heartbeat detection
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();

            ConnectStrategy connectStrategy =
                    ConnectStrategy.http(streamingUri)
                            .headers(headersBuilder.build())
                            .httpClient(httpClient);

            EventSource.Builder eventSourceBuilder = new EventSource.Builder(connectStrategy);

            BackgroundEventSource.Builder builder =
                    new BackgroundEventSource.Builder(
                            new UnleashEventHandler(), eventSourceBuilder);

            BackgroundEventSource newEventSource = builder.build();
            newEventSource.start();
            eventSource = newEventSource;
        } catch (Exception e) {
            LOGGER.error("Failed to start streaming client", e);
        }
    }

    public void stop() {
        try {
            BackgroundEventSource currentEventSource = eventSource;
            if (currentEventSource != null) {
                currentEventSource.close();
                eventSource = null;
            }
        } catch (Exception e) {
            LOGGER.warn("Error stopping streaming client", e);
        }
    }

    private class UnleashEventHandler implements BackgroundEventHandler {

        @Override
        public void onOpen() throws Exception {
            LOGGER.info("Streaming connection established to Unleash server");
        }

        @Override
        public void onClosed() throws Exception {
            LOGGER.info("Streaming connection to Unleash server closed");
        }

        @Override
        public void onMessage(String event, MessageEvent messageEvent) throws Exception {
            try {
                LOGGER.debug(
                        "Received streaming event: {} with data: {}",
                        event,
                        messageEvent.getData());

                switch (event) {
                    case "unleash-connected":
                    case "unleash-updated":
                        streamingUpdateHandler.accept(messageEvent.getData());
                        break;
                    default:
                        LOGGER.debug("Ignoring unknown event type: {}", event);
                }

            } catch (Exception e) {
                LOGGER.error("Error processing streaming event: {}", event, e);
            }
        }

        @Override
        public void onComment(String comment) throws Exception {}

        @Override
        public void onError(Throwable t) {
            streamingErrorHandler.accept(t);
        }
    }
}
