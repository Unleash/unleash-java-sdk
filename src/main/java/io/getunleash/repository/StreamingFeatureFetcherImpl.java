package io.getunleash.repository;

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.StreamHttpErrorException;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.YggdrasilInvalidInputException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.GatedEventEmitter;
import io.getunleash.util.UnleashConfig;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StreamingFeatureFetcherImpl implements FetchWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingFeatureFetcherImpl.class);
    private static final int DEFAULT_MAX_FAILURES = 5;
    private static final long DEFAULT_FAIL_WINDOW_MS = 60_000L;

    private final UnleashConfig config;
    private final GatedEventEmitter eventDispatcher;
    private final UnleashEngine engine;
    private final BackupHandler featureBackupHandler;
    private final FailoverStrategy failoverStrategy;
    private final ModeController modeController;
    private boolean ready;

    private volatile BackgroundEventSource eventSource;

    StreamingFeatureFetcherImpl(
            UnleashConfig config,
            GatedEventEmitter eventDispatcher,
            UnleashEngine engine,
            BackupHandler featureBackupHandler,
            ModeController modeController) {
        this(
                config,
                eventDispatcher,
                engine,
                featureBackupHandler,
                new FailoverStrategy(DEFAULT_MAX_FAILURES, DEFAULT_FAIL_WINDOW_MS),
                modeController);
    }

    StreamingFeatureFetcherImpl(
            UnleashConfig config,
            GatedEventEmitter eventDispatcher,
            UnleashEngine engine,
            BackupHandler featureBackupHandler,
            FailoverStrategy failoverStrategy,
            ModeController modeController) {
        this.config = config;
        this.eventDispatcher = eventDispatcher;
        this.engine = engine;
        this.featureBackupHandler = featureBackupHandler;
        this.failoverStrategy = failoverStrategy;
        this.modeController = modeController;
    }

    public synchronized void start() {
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

    public synchronized void stop() {
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

    void reconnect() {
        stop();
        start();
    }

    synchronized void handleStreamingUpdate(String data) throws YggdrasilInvalidInputException {
        engine.takeState(data);

        String currentState = engine.getState();
        featureBackupHandler.write(currentState);

        ClientFeaturesResponse response = ClientFeaturesResponse.updated(data);
        eventDispatcher.update(response);

        if (!ready) {
            eventDispatcher.ready();
            ready = true;
        }
    }

    void handleStreamingError(Throwable throwable) {
        handleFailoverDecision(toFailEvent(throwable));
    }

    void handleModeChange(String eventData) {
        if (eventData.equals("polling")) {
            FailoverStrategy.ServerEvent failEvent =
                    new FailoverStrategy.ServerEvent(
                            Instant.now(),
                            "Server has explicitly requested switching to polling mode",
                            eventData);
            handleFailoverDecision(failEvent);
        } else {
            LOGGER.debug("Ignoring an unrecognized fetch mode change to {}", eventData);
        }
    }

    void handleServerDisconnect() {
        FailoverStrategy.NetworkEventError failEvent =
                new FailoverStrategy.NetworkEventError(
                        Instant.now(), "Server closed the streaming connection");
        handleFailoverDecision(failEvent);
    }

    private FailoverStrategy.FailEvent toFailEvent(Throwable throwable) {
        Instant now = Instant.now();
        if (throwable instanceof StreamHttpErrorException) {
            int statusCode = ((StreamHttpErrorException) throwable).getCode();
            String message =
                    throwable.getMessage() != null
                            ? throwable.getMessage()
                            : String.format(
                                    "Streaming failed with http status code %d", statusCode);
            return new FailoverStrategy.HttpStatusError(now, message, statusCode);
        }

        // Not an HTTP problem so something has likely gone wrong on the network layer
        String message =
                (throwable != null && throwable.getMessage() != null)
                        ? throwable.getMessage()
                        : "Network error occurred in streaming";
        return new FailoverStrategy.NetworkEventError(now, message);
    }

    private void handleFailoverDecision(FailoverStrategy.FailEvent failEvent) {
        boolean shouldFail = failoverStrategy.shouldFailover(failEvent, Instant.now());
        if (shouldFail) {
            LOGGER.warn(
                    "Streaming failover triggered: {}. Client is switching over to polling mode.",
                    failEvent.getMessage());

            if (modeController != null) {
                modeController.requestFailover();
            } else {
                LOGGER.warn(
                        "No ModeController configured, cannot request failover to polling mode.");
            }
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
            handleServerDisconnect();
        }

        @Override
        public void onMessage(String event, MessageEvent messageEvent) {
            LOGGER.debug(
                    "Received streaming event: {} with data: {}", event, messageEvent.getData());

            switch (event) {
                case "unleash-connected":
                case "unleash-updated":
                    try {
                        handleStreamingUpdate(messageEvent.getData());
                    } catch (YggdrasilInvalidInputException e) {
                        reconnect();
                    }
                    break;
                case "fetch-mode":
                    handleModeChange(messageEvent.getData());
                    break;
                default:
                    LOGGER.debug("Ignoring unknown event type: {}", event);
            }
        }

        @Override
        public void onComment(String comment) throws Exception {
            // gotta implement this because inheritance reasons but we don't care about it
        }

        @Override
        public void onError(Throwable t) {
            handleStreamingError(t);
        }
    }
}
