package io.getunleash.repository;

import static io.getunleash.util.UnleashConfig.UNLEASH_INTERVAL;

import com.google.gson.JsonSyntaxException;
import io.getunleash.UnleashException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.util.OkHttpClientConfigurer;
import io.getunleash.util.UnleashConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpFeatureFetcher implements FeatureFetcher {
    private final HttpUrl toggleUrl;
    private final OkHttpClient client;
    private final String interval;

    public OkHttpFeatureFetcher(UnleashConfig unleashConfig) {
        this.interval = unleashConfig.getFetchTogglesIntervalMillis();
        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("http_cache").toFile();
        } catch (IOException ignored) {
        }
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(unleashConfig.getFetchTogglesConnectTimeout())
                        .callTimeout(unleashConfig.getFetchTogglesReadTimeout())
                        .followRedirects(true);
        if (tempDir != null) {
            builder = builder.cache(new Cache(tempDir, 1024 * 1024 * 50));
        }
        if (unleashConfig.getProxy() != null) {
            builder = builder.proxy(unleashConfig.getProxy());
        }

        this.toggleUrl =
                Objects.requireNonNull(
                        HttpUrl.get(
                                unleashConfig
                                        .getUnleashURLs()
                                        .getFetchTogglesURL(
                                                unleashConfig.getProjectName(),
                                                unleashConfig.getNamePrefix())));
        this.client = OkHttpClientConfigurer.configureInterceptor(unleashConfig, builder.build());
    }

    public OkHttpFeatureFetcher(UnleashConfig unleashConfig, OkHttpClient client) {
        this.interval = unleashConfig.getFetchTogglesIntervalMillis();
        this.client = OkHttpClientConfigurer.configureInterceptor(unleashConfig, client);
        this.toggleUrl =
                Objects.requireNonNull(
                        HttpUrl.get(
                                unleashConfig
                                        .getUnleashURLs()
                                        .getFetchTogglesURL(
                                                unleashConfig.getProjectName(),
                                                unleashConfig.getNamePrefix())));
    }

    @Override
    public ClientFeaturesResponse fetchFeatures() throws UnleashException {
        Request request =
                new Request.Builder()
                        .url(toggleUrl)
                        .get()
                        .addHeader(UNLEASH_INTERVAL, interval)
                        .build();
        int code = 200;
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.networkResponse() != null
                        && response.networkResponse().code() == 304) {
                    return ClientFeaturesResponse.notChanged();
                }
                String features = response.body().string();

                return ClientFeaturesResponse.updated(features);
            } else if (response.code() == 304) {
                return ClientFeaturesResponse.notChanged();
            } else {
                return ClientFeaturesResponse.unavailable(
                        response.code(), Optional.of(toggleUrl.toString()));
            }
        } catch (IOException | NullPointerException ioEx) {
            throw new UnleashException("Could not fetch toggles", ioEx);
        } catch (IllegalStateException | JsonSyntaxException ex) {
            return ClientFeaturesResponse.unavailable(code, Optional.of(toggleUrl.toString()));
        }
    }
}
