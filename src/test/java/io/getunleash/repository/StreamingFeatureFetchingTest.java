package io.getunleash.repository;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.getunleash.DefaultUnleash;
import io.getunleash.SynchronousTestExecutor;
import io.getunleash.Unleash;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.YggdrasilInvalidInputException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.GatedEventEmitter;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

public class StreamingFeatureFetchingTest {

    @RegisterExtension
    static WireMockExtension serverMock =
            WireMockExtension.newInstance()
                    .configureStaticDsl(true)
                    .options(wireMockConfig().dynamicPort())
                    .build();

    private UnleashConfig config;
    private TestSubscriber testSubscriber;
    private SynchronousTestExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        testSubscriber = new TestSubscriber();
        executor = new SynchronousTestExecutor();
        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");

        // Use unique instance ID to avoid conflicts between tests
        String instanceId = "test-instance-" + System.currentTimeMillis();

        config =
                UnleashConfig.builder()
                        .appName("streaming-event-test")
                        .instanceId(instanceId)
                        .unleashAPI(uri)
                        .experimentalStreamingMode()
                        .subscriber(testSubscriber)
                        .scheduledExecutor(executor)
                        .disableMetrics()
                        .build();
    }

    @Test
    void should_handle_unleash_connected_event() throws Exception {
        String hydrationData =
                "{\"events\":[{\"type\":\"hydration\",\"eventId\":1,\"features\":[{\"name\":\"deltaFeature\",\"enabled\":true,\"strategies\":[],\"variants\":[]}],\"segments\":[]}]}";

        stubFor(
                get(urlEqualTo("/api/client/streaming"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "event: unleash-connected\n"
                                                        + "data: "
                                                        + hydrationData
                                                        + "\n\n")));

        Unleash unleash = new DefaultUnleash(config);

        assertThat(unleash).isNotNull();
        assertThat(config.isStreamingMode()).isTrue();

        boolean eventReceived = testSubscriber.awaitTogglesFetched(5, TimeUnit.SECONDS);
        assertThat(eventReceived).isTrue();
        assertThat(testSubscriber.getTogglesFetchedCount()).isGreaterThan(0);

        boolean isEnabled = unleash.isEnabled("deltaFeature");
        assertThat(isEnabled).isTrue();

        unleash.shutdown();

        verify(getRequestedFor(urlMatching("/api/client/streaming")));
    }

    @Test
    void should_handle_unleash_updated_event_and_shutdown() throws Exception {
        String initialHydration =
                "{\"events\":[{\"type\":\"hydration\",\"eventId\":1,\"features\":[{\"name\":\"deltaFeature\",\"enabled\":true,\"strategies\":[],\"variants\":[]}],\"segments\":[]}]}";

        String updateData =
                "{\"events\":[{\"type\":\"feature-updated\",\"eventId\":2,\"feature\":{\"name\":\"deltaFeature\",\"enabled\":false,\"strategies\":[],\"variants\":[]}}]}";

        stubFor(
                get(urlEqualTo("/api/client/streaming"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "event: unleash-connected\n"
                                                        + "data: "
                                                        + initialHydration
                                                        + "\n\n"
                                                        + "event: unleash-updated\n"
                                                        + "data: "
                                                        + updateData
                                                        + "\n\n")));

        Unleash unleash = new DefaultUnleash(config);

        assertThat(unleash).isNotNull();

        boolean eventsReceived = testSubscriber.awaitTogglesFetched(5, TimeUnit.SECONDS, 2);
        assertThat(eventsReceived).isTrue();
        assertThat(testSubscriber.getTogglesFetchedCount()).isGreaterThanOrEqualTo(2);

        boolean updatedResult = unleash.isEnabled("deltaFeature");
        assertThat(updatedResult).isFalse();

        unleash.shutdown();

        verify(getRequestedFor(urlMatching("/api/client/streaming")));
    }

    @Test
    public void should_write_backup_file_when_streaming_update_received() throws Exception {
        UnleashConfig streamingConfig =
                UnleashConfig.builder()
                        .appName("streaming-test")
                        .unleashAPI("http://localhost:4242/api/")
                        .scheduledExecutor(mock(UnleashScheduledExecutor.class))
                        .disableMetrics()
                        .disablePolling()
                        .experimentalStreamingMode()
                        .build();

        FeatureBackupHandlerFile backupHandler = mock(FeatureBackupHandlerFile.class);

        StreamingFeatureFetcherImpl streamingFetcher =
                new StreamingFeatureFetcherImpl(
                        streamingConfig,
                        new GatedEventEmitter(new EventDispatcher(streamingConfig)),
                        new UnleashEngine(),
                        backupHandler,
                        null);

        String streamingData =
                "{\"events\":[{\"type\":\"hydration\",\"eventId\":1,\"features\":[{\"name\":\"testFeature\",\"enabled\":true,\"strategies\":[],\"variants\":[]}],\"segments\":[]}]}";

        streamingFetcher.handleStreamingUpdate(streamingData);

        ArgumentCaptor<String> backupContentCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(backupHandler, times(1)).write(backupContentCaptor.capture());

        String savedBackupContent = backupContentCaptor.getValue();

        assertThat(savedBackupContent).contains("\"features\"");
        assertThat(savedBackupContent).contains("\"segments\"");

        assertThat(savedBackupContent).contains("\"testFeature\"");

        assertThat(savedBackupContent).contains("\"version\":2");
        assertThat(savedBackupContent).contains("\"query\":");

        assertThat(savedBackupContent).doesNotContain("\"events\""); // store state
    }

    @Test
    void should_reconnect_without_last_event_id_header() throws Exception {
        String hydration =
                "{\"events\":[{\"type\":\"hydration\",\"eventId\":1,\"features\":[{\"name\":\"deltaFeature\",\"enabled\":true,\"strategies\":[],\"variants\":[]}],\"segments\":[]}]}";

        String badUpdate = "{not-json";

        stubFor(
                get(urlEqualTo("/api/client/streaming"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "event: unleash-connected\n"
                                                        + "data: "
                                                        + hydration
                                                        + "\n\n"
                                                        + "event: unleash-updated\n"
                                                        + "data: "
                                                        + badUpdate
                                                        + "\n\n")));

        FeatureBackupHandlerFile backupHandler = mock(FeatureBackupHandlerFile.class);
        UnleashEngine mockEngine = mock(UnleashEngine.class);

        doThrow(new YggdrasilInvalidInputException("Invalid input"))
                .when(mockEngine)
                .takeState(badUpdate);

        StreamingFeatureFetcherImpl streamingFetcher =
                new StreamingFeatureFetcherImpl(
                        config,
                        new GatedEventEmitter(new EventDispatcher(config)),
                        mockEngine,
                        backupHandler,
                        null);
        streamingFetcher.start();

        // Wait for reconnect to happen by polling WireMock's request log
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            int count = serverMock.getAllServeEvents().size();
            if (count >= 2) break;
            Thread.sleep(50);
        }

        var serveEvents = serverMock.getAllServeEvents();
        assertThat(serveEvents.size()).isGreaterThanOrEqualTo(2);

        List<LoggedRequest> requests =
                serveEvents.stream()
                        .map(se -> se.getRequest())
                        .filter(req -> req.getMethod().getName().equals("GET"))
                        .filter(req -> req.getUrl().equals("/api/client/streaming"))
                        .collect(Collectors.toList());

        assertThat(requests.size()).isGreaterThanOrEqualTo(2);
        LoggedRequest reconnectionRequest = requests.get(1);
        // The important part - a reconnection should never include the last-event-id header so that
        // we get a fresh hydration
        assertThat(reconnectionRequest.getHeader("Last-Event-ID")).isNull();
        streamingFetcher.stop();
    }

    private static class TestSubscriber implements UnleashSubscriber {
        private final CountDownLatch togglesFetchedLatch = new CountDownLatch(1);
        private final CountDownLatch multipleTogglesFetchedLatch = new CountDownLatch(2);
        private int togglesFetchedCount = 0;
        private List<ClientFeaturesResponse> responses = new ArrayList<>();

        @Override
        public void togglesFetched(ClientFeaturesResponse toggleResponse) {
            togglesFetchedCount++;
            responses.add(toggleResponse);
            togglesFetchedLatch.countDown();
            multipleTogglesFetchedLatch.countDown();
        }

        public boolean awaitTogglesFetched(long timeout, TimeUnit unit)
                throws InterruptedException {
            return togglesFetchedLatch.await(timeout, unit);
        }

        public boolean awaitTogglesFetched(long timeout, TimeUnit unit, int expectedCount)
                throws InterruptedException {
            if (expectedCount <= 1) {
                return togglesFetchedLatch.await(timeout, unit);
            } else {
                return multipleTogglesFetchedLatch.await(timeout, unit);
            }
        }

        public int getTogglesFetchedCount() {
            return togglesFetchedCount;
        }
    }
}
