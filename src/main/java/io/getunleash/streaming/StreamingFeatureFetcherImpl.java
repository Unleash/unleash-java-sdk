package io.getunleash.streaming;

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import io.getunleash.UnleashException;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.UnleashReady;
import io.getunleash.repository.BackupHandler;
import io.getunleash.repository.FetchWorker;
import io.getunleash.util.UnleashConfig;
import java.net.URI;
import java.time.Duration;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamingFeatureFetcherImpl implements FetchWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingFeatureFetcherImpl.class);

    private final UnleashConfig config;
    private final EventDispatcher eventDispatcher;
    private final UnleashEngine engine;
    private final BackupHandler featureBackupHandler;
    private boolean ready;

    private volatile BackgroundEventSource eventSource;

    public StreamingFeatureFetcherImpl(
            UnleashConfig config,
            EventDispatcher eventDispatcher,
            UnleashEngine engine,
            BackupHandler featureBackupHandler) {
        this.config = config;
        this.eventDispatcher = eventDispatcher;
        this.engine = engine;
        this.featureBackupHandler = featureBackupHandler;
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

    synchronized void handleStreamingUpdate(String data) {
        try {
            engine.takeState(data);

            String currentState = engine.getState();
            featureBackupHandler.write(currentState);

            ClientFeaturesResponse response = ClientFeaturesResponse.updated(data);
            eventDispatcher.dispatch(response);

            if (!ready) {
                eventDispatcher.dispatch(new UnleashReady());
                ready = true;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process streaming update", e);
            UnleashException unleashException =
                    new UnleashException("Failed to process streaming update", e);
            eventDispatcher.dispatch(unleashException);
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
                        handleStreamingUpdate(messageEvent.getData());
                        break;
                    default:
                        LOGGER.debug("Ignoring unknown event type: {}", event);
                }

            } catch (Exception e) {
                LOGGER.error(
                        "Error processing streaming event, feature flags will likely not evaluate correctly until application restart or stream re-connect: {}",
                        event,
                        e);
            }
        }

        @Override
        public void onComment(String comment) throws Exception {}

        @Override
        public void onError(Throwable t) {
            UnleashException unleashException =
                    new UnleashException("Streaming connection error", t);
            eventDispatcher.dispatch(unleashException);
        }
    }
}
