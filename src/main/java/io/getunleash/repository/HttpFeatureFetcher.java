package io.getunleash.repository;

import static io.getunleash.util.UnleashConfig.UNLEASH_INTERVAL;

import io.getunleash.UnleashException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.util.UnleashConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpFeatureFetcher implements FeatureFetcher {
    private static final Logger LOG = LoggerFactory.getLogger(HttpFeatureFetcher.class);
    private Optional<String> etag = Optional.empty();

    private final UnleashConfig config;

    private final URL toggleUrl;

    public HttpFeatureFetcher(UnleashConfig config) {
        this.config = config;
        this.toggleUrl =
                config.getUnleashURLs()
                        .getFetchTogglesURL(config.getProjectName(), config.getNamePrefix());
    }

    @Override
    public ClientFeaturesResponse fetchFeatures() throws UnleashException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(this.toggleUrl);
            connection.setRequestProperty(
                    UNLEASH_INTERVAL, this.config.getFetchTogglesIntervalMillis());
            connection.connect();

            return getFeatureResponse(connection, true);
        } catch (IOException e) {
            throw new UnleashException("Could not fetch toggles", e);
        } catch (IllegalStateException e) {
            throw new UnleashException(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ClientFeaturesResponse getFeatureResponse(
            HttpURLConnection request, boolean followRedirect) throws IOException {
        int responseCode = request.getResponseCode();

        if (responseCode < 300) {
            etag = Optional.ofNullable(request.getHeaderField("ETag"));

            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    (InputStream) request.getContent(), StandardCharsets.UTF_8))) {

                String clientFeatures = reader.lines().collect(Collectors.joining("\n"));

                return ClientFeaturesResponse.updated(clientFeatures);
            }
        } else if (followRedirect
                && (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER)) {
            return followRedirect(request);
        } else if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return ClientFeaturesResponse.notChanged();
        } else {
            return ClientFeaturesResponse.unavailable(responseCode, getLocationHeader(request));
        }
    }

    private ClientFeaturesResponse followRedirect(HttpURLConnection request) throws IOException {
        String newUrl =
                getLocationHeader(request)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No Location header found in redirect response."));

        request = openConnection(new URL(newUrl));
        request.connect();
        LOG.info(
                "Redirecting from {} to {}. Please consider updating your config.",
                this.toggleUrl,
                newUrl);

        return getFeatureResponse(request, false);
    }

    private Optional<String> getLocationHeader(HttpURLConnection connection) {
        return Optional.ofNullable(connection.getHeaderField("Location"));
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection connection;
        if (this.config.getProxy() != null) {
            connection = (HttpURLConnection) url.openConnection(this.config.getProxy());
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }
        connection.setConnectTimeout((int) this.config.getFetchTogglesConnectTimeout().toMillis());
        connection.setReadTimeout((int) this.config.getFetchTogglesReadTimeout().toMillis());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        UnleashConfig.setRequestProperties(connection, this.config);

        etag.ifPresent(val -> connection.setRequestProperty("If-None-Match", val));

        connection.setUseCaches(true);

        return connection;
    }
}
