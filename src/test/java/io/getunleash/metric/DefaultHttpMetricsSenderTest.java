package io.getunleash.metric;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.getunleash.util.UnleashConfig.UNLEASH_CONNECTION_ID_HEADER;
import static io.getunleash.util.UnleashConfig.UNLEASH_INTERVAL;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.getunleash.engine.MetricsBucket;
import io.getunleash.impactmetrics.BucketMetricOptions;
import io.getunleash.impactmetrics.CollectedMetric;
import io.getunleash.impactmetrics.Histogram;
import io.getunleash.impactmetrics.InMemoryMetricRegistry;
import io.getunleash.util.UnleashConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefaultHttpMetricsSenderTest {

    @RegisterExtension
    static WireMockExtension serverMock =
            WireMockExtension.newInstance()
                    .configureStaticDsl(true)
                    .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
                    .build();

    @Test
    public void should_send_client_registration() throws URISyntaxException {
        stubFor(
                post(urlEqualTo("/client/register"))
                        .withHeader("UNLEASH-APPNAME", matching("test-app"))
                        .willReturn(aResponse().withStatus(200)));

        URI uri = new URI("http://localhost:" + serverMock.getPort());
        UnleashConfig config = UnleashConfig.builder().appName("test-app").unleashAPI(uri).build();

        DefaultHttpMetricsSender sender = new DefaultHttpMetricsSender(config);
        sender.registerClient(new ClientRegistration(config, LocalDateTime.now(), new HashSet<>()));

        verify(
                postRequestedFor(urlMatching("/client/register"))
                        .withRequestBody(matching(".*appName.*"))
                        .withRequestBody(matching(".*strategies.*"))
                        .withHeader(
                                UNLEASH_CONNECTION_ID_HEADER, matching(config.getConnectionId()))
                        .withHeader("UNLEASH-APPNAME", matching("test-app")));
    }

    @Test
    public void should_send_client_metrics() throws URISyntaxException {
        stubFor(
                post(urlEqualTo("/client/metrics"))
                        .withHeader("UNLEASH-APPNAME", matching("test-app"))
                        .willReturn(aResponse().withStatus(200)));

        URI uri = new URI("http://localhost:" + serverMock.getPort());
        UnleashConfig config = UnleashConfig.builder().appName("test-app").unleashAPI(uri).build();

        DefaultHttpMetricsSender sender = new DefaultHttpMetricsSender(config);
        MetricsBucket bucket = new MetricsBucket(Instant.now(), Instant.now(), null);
        ClientMetrics metrics = new ClientMetrics(config, bucket);
        sender.sendMetrics(metrics);

        verify(
                postRequestedFor(urlMatching("/client/metrics"))
                        .withRequestBody(matching(".*appName.*"))
                        .withRequestBody(matching(".*bucket.*"))
                        .withHeader(
                                UNLEASH_INTERVAL, matching(config.getSendMetricsIntervalMillis()))
                        .withHeader(
                                UNLEASH_CONNECTION_ID_HEADER, matching(config.getConnectionId()))
                        .withHeader("UNLEASH-APPNAME", matching("test-app")));
    }

    @Test
    public void should_handle_service_failure_when_sending_metrics() throws URISyntaxException {
        stubFor(
                post(urlEqualTo("/client/metrics"))
                        .withHeader("UNLEASH-APPNAME", matching("test-app"))
                        .willReturn(aResponse().withStatus(500)));

        URI uri = new URI("http://localhost:" + serverMock.getPort());
        long metricsInterval = 0;
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("test-app")
                        .unleashAPI(uri)
                        .sendMetricsInterval(metricsInterval)
                        .build();

        DefaultHttpMetricsSender sender = new DefaultHttpMetricsSender(config);
        MetricsBucket bucket = new MetricsBucket(Instant.now(), Instant.now(), null);
        ClientMetrics metrics = new ClientMetrics(config, bucket);
        sender.sendMetrics(metrics);

        verify(
                postRequestedFor(urlMatching("/client/metrics"))
                        .withRequestBody(matching(".*appName.*"))
                        .withRequestBody(matching(".*bucket.*"))
                        .withHeader(UNLEASH_INTERVAL, matching(String.valueOf(metricsInterval)))
                        .withHeader("UNLEASH-APPNAME", matching("test-app")));
    }

    @Test
    public void should_send_impact_metrics_with_histogram_and_plus_inf_bucket()
            throws URISyntaxException {
        stubFor(
                post(urlEqualTo("/client/metrics"))
                        .withHeader("UNLEASH-APPNAME", matching("test-app"))
                        .willReturn(aResponse().withStatus(200)));

        URI uri = new URI("http://localhost:" + serverMock.getPort());
        UnleashConfig config = UnleashConfig.builder().appName("test-app").unleashAPI(uri).build();

        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Histogram histogram =
                registry.histogram(
                        new BucketMetricOptions(
                                "test_histogram", "testing histogram", List.of(1.0, 5.0)));

        histogram.observe(0.5);
        histogram.observe(10.0);

        List<CollectedMetric> impactMetrics = registry.collect();

        DefaultHttpMetricsSender sender = new DefaultHttpMetricsSender(config);
        MetricsBucket bucket = new MetricsBucket(Instant.now(), Instant.now(), null);
        ClientMetrics metrics = new ClientMetrics(config, bucket, impactMetrics);
        sender.sendMetrics(metrics);

        verify(
                postRequestedFor(urlMatching("/client/metrics"))
                        .withRequestBody(matching(".*\"impactMetrics\".*"))
                        .withRequestBody(matching(".*\"name\"\\s*:\\s*\"test_histogram\".*"))
                        .withRequestBody(matching(".*\"type\"\\s*:\\s*\"HISTOGRAM\".*"))
                        .withRequestBody(matching(".*\"le\"\\s*:\\s*\"\\+Inf\".*"))
                        .withHeader(
                                UNLEASH_INTERVAL, matching(config.getSendMetricsIntervalMillis()))
                        .withHeader(
                                UNLEASH_CONNECTION_ID_HEADER, matching(config.getConnectionId()))
                        .withHeader("UNLEASH-APPNAME", matching("test-app")));
    }
}
