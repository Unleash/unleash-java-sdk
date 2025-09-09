package io.getunleash;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.UnleashReady;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.repository.*;
import io.getunleash.strategy.Strategy;
import io.getunleash.util.UnleashConfig;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class DefaultUnleashTest {
    private DefaultUnleash sut;
    private EngineProxy engineProxy;
    private UnleashContextProvider contextProvider;
    private EventDispatcher eventDispatcher;

    private String loadMockFeatures(String path) {
        try {
            File file = new File(getClass().getClassLoader().getResource(path).toURI());
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load testdata", ex);
        }
    }

    @RegisterExtension
    static WireMockExtension serverMock =
            WireMockExtension.newInstance()
                    .configureStaticDsl(true)
                    .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
                    .build();

    @BeforeEach
    public void setup() {
        UnleashConfig unleashConfig =
                UnleashConfig.builder().unleashAPI("http://fakeAPI").appName("fakeApp").build();
        engineProxy = mock(EngineProxy.class);
        Map<String, Strategy> strategyMap = new HashMap<>();
        contextProvider = mock(UnleashContextProvider.class);
        eventDispatcher = mock(EventDispatcher.class);

        sut = new DefaultUnleash(unleashConfig, engineProxy, contextProvider, eventDispatcher);
    }

    @Test
    public void should_evaluate_all_toggle_with_context() {

        ToggleBootstrapProvider bootstrapper =
                new ToggleBootstrapProvider() {

                    @Override
                    public Optional<String> read() {
                        return Optional.of(loadMockFeatures("unleash-repo-v2.json"));
                    }
                };

        UnleashConfig unleashConfig =
                UnleashConfig.builder()
                        .unleashAPI("http://fakeAPI")
                        .appName("fakeApp")
                        .toggleBootstrapProvider(bootstrapper)
                        .build();

        Unleash unleash = new DefaultUnleash(unleashConfig);
        List<EvaluatedToggle> toggles = unleash.more().evaluateAllToggles();
        assertThat(toggles).hasSize(5);

        // rather than getting the first toggle, we need to find the toggle with the
        // correct name since this now comes from Yggdrasil and the feature set is
        // backed by
        // hashmap, we can't guarantee a stable ordering
        EvaluatedToggle t1 =
                toggles.stream().filter(t -> t.getName().equals("featureX")).findFirst().get();

        assertThat(t1.getName()).isEqualTo("featureX");
        assertThat(t1.isEnabled()).isTrue();
    }

    @Test
    public void should_allow_fallback_strategy() {
        Strategy fallback = mock(Strategy.class);
        when(fallback.getName()).thenReturn("custom strategy");
        when(fallback.isEnabled(any(), any(UnleashContext.class))).thenReturn(true);

        ToggleBootstrapProvider bootstrapper =
                new ToggleBootstrapProvider() {
                    @Override
                    public Optional<String> read() {
                        return Optional.of(
                                "{\"version\":1,\"features\":[{\"name\":\"toggle1\",\"enabled\":true,\"strategies\":[{\"name\":\"nonexistent\"}]}]}");
                    }
                };

        UnleashConfig unleashConfigWithFallback =
                UnleashConfig.builder()
                        .unleashAPI("http://fakeAPI")
                        .appName("fakeApp")
                        .toggleBootstrapProvider(bootstrapper)
                        .fallbackStrategy(fallback)
                        .build();
        sut = new DefaultUnleash(unleashConfigWithFallback);

        when(contextProvider.getContext()).thenReturn(UnleashContext.builder().build());

        sut.isEnabled("toggle1");

        verify(fallback).isEnabled(any(), any());
    }

    @Test
    public void not_setting_current_time_falls_back_to_correct_now_instant() {

        ToggleBootstrapProvider bootstrapper =
                new ToggleBootstrapProvider() {

                    @Override
                    public Optional<String> read() {
                        return Optional.of(loadMockFeatures("unleash-repo-v2-advanced.json"));
                    }
                };

        UnleashConfig unleashConfigWithFallback =
                UnleashConfig.builder()
                        .unleashAPI("http://fakeAPI")
                        .appName("fakeApp")
                        .toggleBootstrapProvider(bootstrapper)
                        .build();
        sut = new DefaultUnleash(unleashConfigWithFallback);

        boolean enabled = sut.isEnabled("Test.currentTime");
        assertThat(enabled).isTrue();
    }

    @Test
    public void multiple_instantiations_of_the_same_config_gives_errors() {
        ListAppender<ILoggingEvent> appender = new ListAppender();
        appender.start();
        Logger unleashLogger = (Logger) LoggerFactory.getLogger(DefaultUnleash.class);
        unleashLogger.addAppender(appender);
        String appName = "multiple_connection_logging";
        String instanceId = "multiple_connection_instance_id";
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI("http://test:4242")
                        .appName(appName)
                        .apiKey("default:development:1234567890123456")
                        .instanceId(instanceId)
                        .build();
        Unleash unleash1 = new DefaultUnleash(config);
        // We've only instantiated the client once, so no errors should've been logged
        assertThat(appender.list).isEmpty();
        Unleash unleash2 = new DefaultUnleash(config);
        // We've now instantiated the client twice, so we expect an error log line.
        assertThat(appender.list).hasSize(1);
        String id = config.getClientIdentifier();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "You already have 1 clients for AppName ["
                                + appName
                                + "] with instanceId: ["
                                + instanceId
                                + "] running. Please double check your code where you are instantiating the Unleash SDK");
        appender.list.clear();
        Unleash unleash3 = new DefaultUnleash(config);
        // We've now instantiated the client twice, so we expect an error log line.
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "You already have 2 clients for AppName ["
                                + appName
                                + "] with instanceId: ["
                                + instanceId
                                + "] running. Please double check your code where you are instantiating the Unleash SDK");
    }

    @Test
    public void supports_failing_hard_on_multiple_instantiations() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI("http://test:4242")
                        .appName("multiple_connection_exception")
                        .apiKey("default:development:1234567890123456")
                        .instanceId("multiple_connection_exception")
                        .build();
        String id = config.getClientIdentifier();
        Unleash unleash1 = new DefaultUnleash(config);
        assertThatThrownBy(
                        () -> {
                            Unleash unleash2 = new DefaultUnleash(config, null, null, null, true);
                        })
                .isInstanceOf(RuntimeException.class)
                .withFailMessage(
                        "You already have 1 clients for Unleash Configuration ["
                                + id
                                + "] running. Please double check your code where you are instantiating the Unleash SDK");
    }

    @Test
    public void synchronous_fetch_on_initialisation_fails_on_initialization() {
        IsReadyTestSubscriber readySubscriber = new IsReadyTestSubscriber();
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI("http://wrong:4242")
                        .appName("wrong_upstream")
                        .apiKey("default:development:1234567890123456")
                        .instanceId("multiple_connection_exception")
                        .synchronousFetchOnInitialisation(true)
                        .subscriber(readySubscriber)
                        .build();

        assertThatThrownBy(() -> new DefaultUnleash(config)).isInstanceOf(UnleashException.class);
        assertThat(readySubscriber.ready).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 500})
    public void synchronous_fetch_on_initialisation_fails_on_non_200_response(int code)
            throws URISyntaxException {
        mockUnleashAPI(code);
        IsReadyTestSubscriber readySubscriber = new IsReadyTestSubscriber();
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI(new URI("http://localhost:" + serverMock.getPort() + "/api/"))
                        .appName("wrong_upstream")
                        .apiKey("default:development:1234567890123456")
                        .instanceId("non-200")
                        .synchronousFetchOnInitialisation(true)
                        .subscriber(readySubscriber)
                        .build();

        assertThatThrownBy(() -> new DefaultUnleash(config)).isInstanceOf(UnleashException.class);
        assertThat(readySubscriber.ready).isFalse();
    }

    @Test
    public void synchronous_fetch_on_initialisation_switches_to_ready_on_200()
            throws URISyntaxException {
        mockUnleashAPI(200);
        IsReadyTestSubscriber readySubscriber = new IsReadyTestSubscriber();
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI(new URI("http://localhost:" + serverMock.getPort() + "/api/"))
                        .appName("wrong_upstream")
                        .apiKey("default:development:1234567890123456")
                        .instanceId("with-success-response")
                        .synchronousFetchOnInitialisation(true)
                        .subscriber(readySubscriber)
                        .build();
        new DefaultUnleash(config);
        assertThat(readySubscriber.ready).isTrue();
    }

    private void mockUnleashAPI(int featuresStatusCode) {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(featuresStatusCode)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(loadMockFeatures("unleash-repo-v2.json"))));

        stubFor(post(urlEqualTo("/api/client/register")).willReturn(aResponse().withStatus(200)));
    }

    @Test
    public void asynchronous_fetch_on_initialisation_fails_silently_and_retries()
            throws InterruptedException {
        FeatureFetcher fetcher = mock(FeatureFetcher.class);
        when(fetcher.fetchFeatures())
                .thenThrow(UnleashException.class)
                .thenReturn(ClientFeaturesResponse.updated("doesn't matter for this test"));
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI("http://wrong:4242")
                        .appName("wrong_upstream")
                        .apiKey("default:development:1234567890123456")
                        .instanceId("multiple_connection_exception")
                        .fetchTogglesInterval(1)
                        .unleashFeatureFetcherFactory((UnleashConfig c) -> fetcher)
                        .build();

        Unleash unleash = new DefaultUnleash(config);
        Thread.sleep(1);
        verify(fetcher, times(1)).fetchFeatures();
        Thread.sleep(1200);
        verify(fetcher, times(2)).fetchFeatures();
    }

    @Test
    public void client_identifier_handles_api_key_being_null() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI("http://test:4242")
                        .appName("multiple_connection")
                        .instanceId("testing_multiple")
                        .build();
        String id = config.getClientIdentifier();
        assertThat(id)
                .isEqualTo("f83eb743f4c8dc41294aafb96f454763e5a90b96db8b7040ddc505d636bdb243");
    }

    private static class IsReadyTestSubscriber implements UnleashSubscriber {
        public boolean ready = false;

        public void onReady(UnleashReady unleashReady) {
            this.ready = true;
        }
    }
}
