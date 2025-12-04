package io.getunleash.repository;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.getunleash.FeatureDefinition;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.util.UnleashConfig;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OkHttpFeatureFetcherTest {
    @RegisterExtension
    static WireMockExtension serverMock =
            WireMockExtension.newInstance()
                    .configureStaticDsl(true)
                    .options(wireMockConfig().dynamicPort())
                    .build();

    @Test
    public void happy_path_test() throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("features-v0.json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        ClientFeaturesResponse response = okHttpToggleFetcher.fetchFeatures();

        Optional<FeatureDefinition> featureX =
                response.getFeatures().stream()
                        .filter(f -> f.getName().equals("featureX"))
                        .findFirst();

        assertThat(featureX).isNotEmpty();

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @Test
    public void should_include_etag_in_second_request() throws URISyntaxException {
        // First fetch
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("ETag", "AZ12")
                                        .withBodyFile("features-v1-with-variants.json")));

        // Second fetch
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("If-None-Match", equalTo("AZ12"))
                        .willReturn(
                                aResponse()
                                        .withStatus(304)
                                        .withHeader("Content-Type", "application/json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);

        ClientFeaturesResponse response1 = okHttpToggleFetcher.fetchFeatures();
        ClientFeaturesResponse response2 = okHttpToggleFetcher.fetchFeatures();
        verify(
                1,
                getRequestedFor(urlEqualTo("/api/client/features")).withoutHeader("If-None-Match"));
        verify(
                1,
                getRequestedFor(urlEqualTo("/api/client/features"))
                        .withHeader("If-None-Match", equalTo("AZ12")));
        assertThat(response1.getStatus()).isEqualTo(ClientFeaturesResponse.Status.CHANGED);

        assertThat(response2.getStatus()).isEqualTo(ClientFeaturesResponse.Status.NOT_CHANGED);
    }

    @Test
    @ExtendWith(UnleashExceptionExtension.class)
    public void given_empty_body() throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        okHttpToggleFetcher.fetchFeatures();

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @Test
    @ExtendWith(UnleashExceptionExtension.class)
    public void given_json_without_feature_field() throws Exception {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{}")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        okHttpToggleFetcher.fetchFeatures();

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @Test
    public void should_handle_not_changed() throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(304)
                                        .withHeader("Content-Type", "application/json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        ClientFeaturesResponse response = okHttpToggleFetcher.fetchFeatures();
        assertThat(response.getStatus()).isEqualTo(ClientFeaturesResponse.Status.NOT_CHANGED);

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER
            })
    public void should_handle_redirect(int responseCode) throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(responseCode)
                                        .withHeader(
                                                "Location",
                                                "http://localhost:"
                                                        + serverMock.getPort()
                                                        + "/api/v2/client/features")));
        stubFor(
                get(urlEqualTo("/api/v2/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(HttpURLConnection.HTTP_OK)
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("features-v1.json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        ClientFeaturesResponse response = okHttpToggleFetcher.fetchFeatures();
        assertThat(response.getStatus()).isEqualTo(ClientFeaturesResponse.Status.CHANGED);

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
        verify(
                getRequestedFor(urlMatching("/api/v2/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 503})
    public void should_handle_errors(int httpCode) throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(httpCode)
                                        .withHeader("Content-Type", "application/json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        ClientFeaturesResponse response = okHttpToggleFetcher.fetchFeatures();
        assertThat(response.getStatus()).isEqualTo(ClientFeaturesResponse.Status.UNAVAILABLE);
        assertThat(response.getHttpStatusCode()).isEqualTo(httpCode);

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @Test
    public void should_not_set_empty_ifNoneMatchHeader() throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("features-v1.json")));

        URI uri = new URI("http://localhost:" + serverMock.getPort() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher okHttpToggleFetcher = new OkHttpFeatureFetcher(config);
        okHttpToggleFetcher.fetchFeatures();

        verify(getRequestedFor(urlMatching("/api/client/features")).withoutHeader("If-None-Match"));
    }

    @Test
    public void should_add_project_filter_to_toggles_url_if_config_has_it_set()
            throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features?project=name"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("features-v1.json")));
        URI uri = new URI(serverMock.baseUrl() + "/api/");
        UnleashConfig config =
                UnleashConfig.builder().appName("test").unleashAPI(uri).projectName("name").build();
        OkHttpFeatureFetcher okHttpFeatureFetcher = new OkHttpFeatureFetcher(config);
        okHttpFeatureFetcher.fetchFeatures();
        verify(getRequestedFor(urlMatching("/api/client/features\\?project=name")));
    }

    @Test
    public void happy_path_test_with_variants_and_segments() throws URISyntaxException {
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("features-v2-with-segments.json")));
        URI uri = new URI(serverMock.baseUrl() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        OkHttpFeatureFetcher fetcher = new OkHttpFeatureFetcher(config);
        ClientFeaturesResponse response = fetcher.fetchFeatures();

        Optional<FeatureDefinition> featureX =
                response.getFeatures().stream()
                        .filter(f -> f.getName().equals("featureX"))
                        .findFirst();

        assertThat(featureX).isNotEmpty();

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }

    @Test
    public void should_include_client_specification_version_in_header() throws URISyntaxException {
        URI uri = new URI(serverMock.baseUrl() + "/api/");
        UnleashConfig config = UnleashConfig.builder().appName("test").unleashAPI(uri).build();
        stubFor(
                get(urlEqualTo("/api/client/features"))
                        .withHeader("Accept", equalTo("application/json"))
                        .withHeader(
                                "Unleash-Client-Spec",
                                equalTo(config.getClientSpecificationVersion()))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBodyFile("features-v2-with-segments.json")));
        OkHttpFeatureFetcher fetcher = new OkHttpFeatureFetcher(config);
        ClientFeaturesResponse response = fetcher.fetchFeatures();
        Optional<FeatureDefinition> featureX =
                response.getFeatures().stream()
                        .filter(f -> f.getName().equals("featureX"))
                        .findFirst();

        assertThat(featureX).isNotEmpty();

        verify(
                getRequestedFor(urlMatching("/api/client/features"))
                        .withHeader("Content-Type", matching("application/json")));
    }
}
