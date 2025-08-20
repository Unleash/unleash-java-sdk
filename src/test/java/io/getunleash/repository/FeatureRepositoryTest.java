package io.getunleash.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.getunleash.DefaultUnleash;
import io.getunleash.FeatureDefinition;
import io.getunleash.Unleash;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.streaming.StreamingFeatureFetcher;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

public class FeatureRepositoryTest {
    FeatureBackupHandlerFile backupHandler;
    ToggleBootstrapProvider bootstrapHandler;
    HttpFeatureFetcher fetcher;
    StreamingFeatureFetcher streamingFetcher;
    UnleashConfig defaultConfig;

    private String loadMockFeatures(String path) {
        try {
            File file = new File(getClass().getClassLoader().getResource(path).toURI());
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load testdata", ex);
        }
    }

    private UnleashConfig.Builder defaultConfigBuilder() {
        return new UnleashConfig.Builder()
                .appName("test")
                .unleashAPI("http://localhost:4242/api/")
                .scheduledExecutor(mock(UnleashScheduledExecutor.class))
                .fetchTogglesInterval(200L)
                .synchronousFetchOnInitialisation(false)
                .disableMetrics()
                .disablePolling();
    }

    @BeforeEach
    public void setUp() {
        backupHandler = mock(FeatureBackupHandlerFile.class);
        bootstrapHandler = mock(ToggleBootstrapProvider.class);
        fetcher = mock(HttpFeatureFetcher.class);
        streamingFetcher = mock(StreamingFeatureFetcher.class);

        defaultConfig = defaultConfigBuilder().build();
    }

    @Test
    public void no_backup_file_and_no_repository_available_should_give_empty_repo() {
        when(backupHandler.read()).thenReturn(Optional.empty());
        when(bootstrapHandler.read()).thenReturn(Optional.empty());

        FeatureRepository featureRepository =
                new FeatureRepositoryImpl(defaultConfig, new UnleashEngine());

        List<FeatureDefinition> knownToggles =
                featureRepository.listKnownToggles().collect(Collectors.toList());

        assertEquals(0, knownToggles.size());
    }

    @Test
    public void backup_toggles_should_be_loaded_at_startup() {
        when(backupHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));

        when(bootstrapHandler.read()).thenReturn(Optional.empty());

        FeatureRepository featureRepository =
                new FeatureRepositoryImpl(defaultConfig, backupHandler, new UnleashEngine());

        List<FeatureDefinition> knownToggles =
                featureRepository.listKnownToggles().collect(Collectors.toList());
        assertEquals(5, knownToggles.size());
    }

    @Test
    public void feature_toggles_should_be_updated() {
        UnleashScheduledExecutor executor = mock(UnleashScheduledExecutor.class);
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);

        UnleashConfig config =
                new UnleashConfig.Builder()
                        .appName("test")
                        .unleashAPI("http://localhost:4242/api/")
                        .scheduledExecutor(executor)
                        .fetchTogglesInterval(200L)
                        .synchronousFetchOnInitialisation(false)
                        .build();

        when(backupHandler.read()).thenReturn(Optional.empty());

        FeatureRepository featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        verify(executor).setInterval(runnableArgumentCaptor.capture(), anyLong(), anyLong());
        verify(fetcher, times(0)).fetchFeatures();

        ClientFeaturesResponse response =
                ClientFeaturesResponse.updated(loadMockFeatures("unleash-repo-v2.json"));
        when(fetcher.fetchFeatures()).thenReturn(response);
        runnableArgumentCaptor.getValue().run();

        verify(fetcher, times(1)).fetchFeatures();
        List<FeatureDefinition> features =
                featureRepository.listKnownToggles().collect(Collectors.toList());
        assertEquals(5, features.size());
    }

    @Test
    public void should_perform_synchronous_fetch_on_initialisation() {
        UnleashConfig config =
                defaultConfigBuilder().synchronousFetchOnInitialisation(true).build();

        when(backupHandler.read()).thenReturn(Optional.empty());

        ClientFeaturesResponse response =
                ClientFeaturesResponse.updated(loadMockFeatures("unleash-repo-v2.json"));
        when(fetcher.fetchFeatures()).thenReturn(response);

        FeatureRepository featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        verify(fetcher, times(1)).fetchFeatures();
    }

    @Test
    public void should_not_perform_synchronous_fetch_on_initialisation() {
        UnleashConfig config =
                defaultConfigBuilder().synchronousFetchOnInitialisation(false).build();

        when(backupHandler.read()).thenReturn(Optional.empty());

        ClientFeaturesResponse response =
                ClientFeaturesResponse.updated(loadMockFeatures("unleash-repo-v2.json"));

        when(fetcher.fetchFeatures()).thenReturn(response);

        FeatureRepositoryImpl featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        verify(fetcher, times(0)).fetchFeatures();
    }

    @Test
    public void should_read_from_bootstrap_location_if_backup_was_empty() {
        when(backupHandler.read()).thenReturn(Optional.empty());
        when(bootstrapHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));

        UnleashConfig config =
                defaultConfigBuilder().toggleBootstrapProvider(bootstrapHandler).build();

        FeatureRepository featureRepository =
                new FeatureRepositoryImpl(config, backupHandler, new UnleashEngine());

        List<FeatureDefinition> knownToggles =
                featureRepository.listKnownToggles().collect(Collectors.toList());
        assertEquals(5, knownToggles.size());
    }

    @Test
    public void should_return_enabled_property_on_known_toggles() {
        when(backupHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));

        when(bootstrapHandler.read()).thenReturn(Optional.empty());

        FeatureRepository featureRepository =
                new FeatureRepositoryImpl(defaultConfig, backupHandler, new UnleashEngine());

        List<FeatureDefinition> knownToggles =
                featureRepository.listKnownToggles().collect(Collectors.toList());

        assertEquals(5, knownToggles.size());
        FeatureDefinition featureY =
                knownToggles.stream().filter(f -> f.getName().equals("featureY")).findFirst().get();
        assertThat(featureY.environmentEnabled()).isFalse();

        FeatureDefinition featureX =
                knownToggles.stream().filter(f -> f.getName().equals("featureX")).findFirst().get();
        assertThat(featureX.environmentEnabled()).isTrue();
    }

    @Test
    public void should_not_read_bootstrap_if_backup_was_found() {

        when(backupHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));
        when(bootstrapHandler.read()).thenReturn(Optional.empty());

        UnleashConfig config =
                defaultConfigBuilder().toggleBootstrapProvider(bootstrapHandler).build();

        FeatureRepositoryImpl featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        verify(bootstrapHandler, times(0)).read();
    }

    @Test
    public void shouldCallStartupExceptionHandlerIfStartupFails() {
        ToggleBootstrapProvider toggleBootstrapProvider = mock(ToggleBootstrapProvider.class);
        UnleashScheduledExecutor executor = mock(UnleashScheduledExecutor.class);
        AtomicBoolean failed = new AtomicBoolean(false);
        UnleashConfig config =
                UnleashConfig.builder()
                        .synchronousFetchOnInitialisation(true)
                        .startupExceptionHandler(
                                (e) -> {
                                    failed.set(true);
                                })
                        .appName("test-sync-update")
                        .scheduledExecutor(executor)
                        .unleashAPI("http://localhost:8080")
                        .toggleBootstrapProvider(toggleBootstrapProvider)
                        .build();

        Unleash unleash = new DefaultUnleash(config);
        assertThat(failed).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    public void should_increase_to_max_interval_when_code(int code)
            throws URISyntaxException, IOException {
        TestRunner runner = new TestRunner();
        ToggleBootstrapProvider toggleBootstrapProvider = mock(ToggleBootstrapProvider.class);
        when(toggleBootstrapProvider.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));
        UnleashConfig config =
                UnleashConfig.builder()
                        .synchronousFetchOnInitialisation(false)
                        .appName("test-sync-update")
                        .scheduledExecutor(runner.executor)
                        .unleashAPI("http://localhost:8080")
                        .build();
        when(backupHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));

        FeatureRepositoryImpl featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        runner.assertThatFetchesAndReceives(
                ClientFeaturesResponse.Status.CHANGED, 200); // set it ready

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, code);
        assertThat(featureRepository.getFailures()).isEqualTo(1);
        assertThat(featureRepository.getSkips()).isEqualTo(20);
        for (int i = 0; i < 20; i++) {
            runner.assertThatSkipsNextRun();
        }
        assertThat(featureRepository.getFailures()).isEqualTo(1);
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getFailures()).isEqualTo(0);
        assertThat(featureRepository.getSkips()).isEqualTo(0);
    }

    @Test
    public void should_incrementally_increase_interval_as_we_receive_too_many_requests()
            throws URISyntaxException, IOException {
        TestRunner runner = new TestRunner();
        ToggleBootstrapProvider toggleBootstrapProvider = mock(ToggleBootstrapProvider.class);
        when(toggleBootstrapProvider.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));
        UnleashConfig config =
                UnleashConfig.builder()
                        .synchronousFetchOnInitialisation(false)
                        .appName("test-sync-update")
                        .scheduledExecutor(runner.executor)
                        .unleashAPI("http://localhost:8080")
                        .build();
        when(backupHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));

        FeatureRepositoryImpl featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 429);
        // client is not ready don't count errors or skips
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(0);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 429);
        // client is not ready don't count errors or skips
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(0);

        // this changes the client to ready
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.CHANGED, 200);
        assertThat(featureRepository.getSkips()).isEqualTo(0);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 429);
        assertThat(featureRepository.getSkips()).isEqualTo(1);
        assertThat(featureRepository.getFailures()).isEqualTo(1);

        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(1);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 429);
        assertThat(featureRepository.getSkips()).isEqualTo(2);
        assertThat(featureRepository.getFailures()).isEqualTo(2);

        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(1);
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(2);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 429);
        assertThat(featureRepository.getSkips()).isEqualTo(3);
        assertThat(featureRepository.getFailures()).isEqualTo(3);

        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(3);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getSkips()).isEqualTo(2);
        assertThat(featureRepository.getFailures()).isEqualTo(2);

        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(2);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getSkips()).isEqualTo(1);
        assertThat(featureRepository.getFailures()).isEqualTo(1);

        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(1);

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(0);
    }

    @Test
    public void server_errors_should_incrementally_increase_interval()
            throws URISyntaxException, IOException {
        TestRunner runner = new TestRunner();
        ToggleBootstrapProvider toggleBootstrapProvider = mock(ToggleBootstrapProvider.class);
        when(toggleBootstrapProvider.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));
        UnleashConfig config =
                UnleashConfig.builder()
                        .synchronousFetchOnInitialisation(false)
                        .appName("test-sync-update")
                        .scheduledExecutor(runner.executor)
                        .unleashAPI("http://localhost:8080")
                        .build();
        when(backupHandler.read())
                .thenReturn(Optional.of(loadMockFeatures("unleash-repo-v2.json")));

        FeatureRepositoryImpl featureRepository =
                new FeatureRepositoryImpl(
                        config,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        runner.assertThatFetchesAndReceives(
                ClientFeaturesResponse.Status.CHANGED, 200); // set it ready

        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 500);
        assertThat(featureRepository.getSkips()).isEqualTo(1);
        assertThat(featureRepository.getFailures()).isEqualTo(1);
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(1);
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 502);
        assertThat(featureRepository.getSkips()).isEqualTo(2);
        assertThat(featureRepository.getFailures()).isEqualTo(2);
        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(2);
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.UNAVAILABLE, 503);
        assertThat(featureRepository.getSkips()).isEqualTo(3);
        assertThat(featureRepository.getFailures()).isEqualTo(3);
        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(3);
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getSkips()).isEqualTo(2);
        assertThat(featureRepository.getFailures()).isEqualTo(2);
        runner.assertThatSkipsNextRun();
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(2);
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getSkips()).isEqualTo(1);
        assertThat(featureRepository.getFailures()).isEqualTo(1);
        runner.assertThatSkipsNextRun();
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(1);
        runner.assertThatFetchesAndReceives(ClientFeaturesResponse.Status.NOT_CHANGED, 304);
        assertThat(featureRepository.getSkips()).isEqualTo(0);
        assertThat(featureRepository.getFailures()).isEqualTo(0);
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

        when(backupHandler.read()).thenReturn(Optional.empty());

        FeatureRepositoryImpl repository =
                new FeatureRepositoryImpl(
                        streamingConfig,
                        backupHandler,
                        new UnleashEngine(),
                        fetcher,
                        streamingFetcher,
                        bootstrapHandler);

        String streamingData =
                "{\"events\":[{\"type\":\"hydration\",\"eventId\":1,\"features\":[{\"name\":\"testFeature\",\"enabled\":true,\"strategies\":[],\"variants\":[]}],\"segments\":[]}]}";

        repository.handleStreamingUpdate(streamingData);

        ArgumentCaptor<String> backupContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(backupHandler, times(1)).write(backupContentCaptor.capture());

        String savedBackupContent = backupContentCaptor.getValue();

        assertThat(savedBackupContent).contains("\"features\"");
        assertThat(savedBackupContent).contains("\"segments\"");

        assertThat(savedBackupContent).contains("\"testFeature\"");

        assertThat(savedBackupContent).contains("\"version\":2"); // Engine state has version field
        assertThat(savedBackupContent).contains("\"query\":"); // Engine state has query field

        assertThat(savedBackupContent).doesNotContain("\"events\""); // No events array wrapper
    }

    private class TestRunner {

        private final UnleashScheduledExecutor executor;
        private final ArgumentCaptor<Runnable> runnableArgumentCaptor;
        private int count = 0;

        private boolean initialized = false;

        public TestRunner() {
            this.executor = mock(UnleashScheduledExecutor.class);
            this.runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        }

        private void ensureInitialized() {
            if (!initialized) {
                verify(executor)
                        .setInterval(runnableArgumentCaptor.capture(), anyLong(), anyLong());
                initialized = true;
            }
        }

        public void assertThatFetchesAndReceives(
                ClientFeaturesResponse.Status status, int statusCode) {
            ensureInitialized();
            ClientFeaturesResponse response = null;
            if (status == ClientFeaturesResponse.Status.CHANGED) {
                response = ClientFeaturesResponse.updated(loadMockFeatures("unleash-repo-v2.json"));
            } else if (status == ClientFeaturesResponse.Status.UNAVAILABLE) {
                response = ClientFeaturesResponse.unavailable(statusCode, Optional.of(""));
            } else if (status == ClientFeaturesResponse.Status.NOT_CHANGED) {
                response = ClientFeaturesResponse.notChanged();
            } else {
                throw new IllegalArgumentException("Unknown status: " + status);
            }

            when(fetcher.fetchFeatures()).thenReturn(response);
            runnableArgumentCaptor.getValue().run();

            verify(fetcher, times(++count)).fetchFeatures();
        }

        public void assertThatSkipsNextRun() {
            ensureInitialized();
            runnableArgumentCaptor.getValue().run();
            verify(fetcher, times(count)).fetchFeatures();
        }
    }
}
