package io.getunleash.event;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.getunleash.event.ClientFeaturesResponse.Status.CHANGED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.getunleash.DefaultUnleash;
import io.getunleash.SynchronousTestExecutor;
import io.getunleash.Unleash;
import io.getunleash.UnleashException;
import io.getunleash.metric.ClientRegistration;
import io.getunleash.util.UnleashConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SubscriberTest {

    @RegisterExtension
    static WireMockExtension serverMock =
            WireMockExtension.newInstance()
                    .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
                    .build();

    private TestSubscriber testSubscriber = new TestSubscriber();
    private UnleashConfig unleashConfig;

    @BeforeEach
    void setup() {
        unleashConfig =
                new UnleashConfig.Builder()
                        .appName(SubscriberTest.class.getSimpleName())
                        .instanceId(SubscriberTest.class.getSimpleName())
                        .synchronousFetchOnInitialisation(true)
                        .unleashAPI("http://localhost:" + serverMock.getPort())
                        .subscriber(testSubscriber)
                        .scheduledExecutor(new SynchronousTestExecutor())
                        .build();
    }

    @Test
    void subscribersAreNotified() {
        serverMock.stubFor(post("/client/register").willReturn(ok()));
        serverMock.stubFor(post("/client/metrics").willReturn(ok()));
        serverMock.stubFor(
                get("/client/features")
                        .willReturn(
                                ok().withHeader("Content-Type", "application/json")
                                        .withBody("{\"features\": [], \"version\": 2 }")));
        Unleash unleash = new DefaultUnleash(unleashConfig);

        unleash.isEnabled("myFeature");
        unleash.isEnabled("myFeature");
        unleash.isEnabled("myFeature");

        assertThat(testSubscriber.togglesFetchedCounter)
                .isEqualTo(2); // Server successfully returns, we call synchronous fetch and
        // schedule
        // once, so 2 calls.
        assertThat(testSubscriber.status).isEqualTo(CHANGED);
        assertThat(testSubscriber.toggleEvaluatedCounter).isEqualTo(3);
        assertThat(testSubscriber.toggleName).isEqualTo("myFeature");
        assertThat(testSubscriber.toggleEnabled).isFalse();
        assertThat(testSubscriber.errors).isEmpty();

        // assertThat(testSubscriber.events).filteredOn(e -> e instanceof TogglesBootstrapped)
        //         .hasSize(1);
        assertThat(testSubscriber.events).filteredOn(e -> e instanceof UnleashReady).hasSize(1);
        assertThat(testSubscriber.events).filteredOn(e -> e instanceof ToggleEvaluated).hasSize(3);
        assertThat(testSubscriber.events)
                .filteredOn(e -> e instanceof ClientFeaturesResponse)
                .hasSize(2);
        assertThat(testSubscriber.events)
                .filteredOn(e -> e instanceof ClientRegistration)
                .hasSize(1);
    }

    private class TestSubscriber implements UnleashSubscriber {

        private int togglesFetchedCounter;
        private ClientFeaturesResponse.Status status;

        private int toggleEvaluatedCounter;
        private String toggleName;
        private boolean toggleEnabled;

        private List<UnleashEvent> events = new ArrayList<>();
        private List<UnleashException> errors = new ArrayList<>();

        @Override
        public void on(UnleashEvent unleashEvent) {
            this.events.add(unleashEvent);
        }

        @Override
        public void onError(UnleashException unleashException) {
            this.errors.add(unleashException);
        }

        @Override
        public void toggleEvaluated(ToggleEvaluated toggleEvaluated) {
            this.toggleEvaluatedCounter++;
            this.toggleName = toggleEvaluated.getToggleName();
            this.toggleEnabled = toggleEvaluated.isEnabled();
        }

        @Override
        public void togglesFetched(ClientFeaturesResponse toggleResponse) {
            this.togglesFetchedCounter++;
            this.status = toggleResponse.getStatus();
        }
    }
}
