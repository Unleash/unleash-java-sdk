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
import io.getunleash.engine.UnleashEngine;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class FeatureRepositoryTest {
    FeatureBackupHandlerFile backupHandler;
    ToggleBootstrapProvider bootstrapHandler;
    HttpFeatureFetcher fetcher;
    FetchWorker streamingFetcher;
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
        streamingFetcher = mock(FetchWorker.class);

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

        new DefaultUnleash(config);
        assertThat(failed).isTrue();
    }
}
