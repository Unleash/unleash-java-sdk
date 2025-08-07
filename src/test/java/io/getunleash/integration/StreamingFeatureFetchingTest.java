package io.getunleash.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.getunleash.DefaultUnleash;
import io.getunleash.SynchronousTestExecutor;
import io.getunleash.Unleash;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.util.UnleashConfig;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
                        .streamingMode()
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
