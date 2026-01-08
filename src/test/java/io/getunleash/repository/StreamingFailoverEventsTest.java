package io.getunleash.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.launchdarkly.eventsource.StreamHttpErrorException;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.GatedEventEmitter;
import io.getunleash.util.UnleashConfig;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StreamingFailoverEventsTest {

    private UnleashConfig config;
    private GatedEventEmitter dispatcher;
    private UnleashEngine engine;
    private BackupHandler backupHandler;

    @BeforeEach
    void setup() {
        config =
                UnleashConfig.builder()
                        .appName("streaming-failover-test")
                        .unleashAPI("http://localhost:4242/api/")
                        .instanceId("failover-test")
                        .experimentalStreamingMode()
                        .disableMetrics()
                        .build();
        dispatcher = new GatedEventEmitter(new EventDispatcher(config));
        engine = new UnleashEngine();
        backupHandler = mock(BackupHandler.class);
    }

    @Test
    void fetch_mode_polling_event_is_forwarded_as_server_hint() {
        FailoverStrategy strategy = mock(FailoverStrategy.class);
        when(strategy.shouldFailover(any(FailoverStrategy.FailEvent.class), any(Instant.class)))
                .thenReturn(false);
        StreamingFeatureFetcherImpl fetcher = newFetcher(strategy);

        fetcher.handleModeChange("polling");

        ArgumentCaptor<FailoverStrategy.FailEvent> captor =
                ArgumentCaptor.forClass(FailoverStrategy.FailEvent.class);
        verify(strategy).shouldFailover(captor.capture(), any(Instant.class));

        assertThat(captor.getValue()).isInstanceOf(FailoverStrategy.ServerEvent.class);
        FailoverStrategy.ServerEvent event = (FailoverStrategy.ServerEvent) captor.getValue();
        assertThat(event.getEvent()).isEqualTo("polling");
        assertThat(event.getMessage())
                .isEqualTo("Server has explicitly requested switching to polling mode");
    }

    @Test
    void http_errors_are_forwarded_as_http_status_events() {
        FailoverStrategy strategy = mock(FailoverStrategy.class);
        when(strategy.shouldFailover(any(FailoverStrategy.FailEvent.class), any(Instant.class)))
                .thenReturn(false);
        StreamingFeatureFetcherImpl fetcher = newFetcher(strategy);

        StreamHttpErrorException exception = mock(StreamHttpErrorException.class);
        when(exception.getCode()).thenReturn(503);
        when(exception.getMessage()).thenReturn("503 from upstream");

        fetcher.handleStreamingError(exception);

        ArgumentCaptor<FailoverStrategy.FailEvent> captor =
                ArgumentCaptor.forClass(FailoverStrategy.FailEvent.class);
        ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(strategy).shouldFailover(captor.capture(), timestampCaptor.capture());

        assertThat(captor.getValue()).isInstanceOf(FailoverStrategy.HttpStatusError.class);
        FailoverStrategy.HttpStatusError event =
                (FailoverStrategy.HttpStatusError) captor.getValue();
        assertThat(event.getStatusCode()).isEqualTo(503);
        assertThat(event.getMessage()).isEqualTo("503 from upstream");
        assertThat(event.getOccurredAt()).isBeforeOrEqualTo(timestampCaptor.getValue());
    }

    @Test
    void server_disconnect_is_treated_as_network_error() {
        FailoverStrategy strategy = mock(FailoverStrategy.class);
        when(strategy.shouldFailover(any(FailoverStrategy.FailEvent.class), any(Instant.class)))
                .thenReturn(false);
        StreamingFeatureFetcherImpl fetcher = newFetcher(strategy);

        fetcher.handleServerDisconnect();

        ArgumentCaptor<FailoverStrategy.FailEvent> captor =
                ArgumentCaptor.forClass(FailoverStrategy.FailEvent.class);
        verify(strategy).shouldFailover(captor.capture(), any(Instant.class));

        assertThat(captor.getValue()).isInstanceOf(FailoverStrategy.NetworkEventError.class);
        assertThat(captor.getValue().getMessage())
                .isEqualTo("Server closed the streaming connection");
    }

    private StreamingFeatureFetcherImpl newFetcher(FailoverStrategy strategy) {
        return new StreamingFeatureFetcherImpl(
                config, dispatcher, engine, backupHandler, strategy, null);
    }
}
