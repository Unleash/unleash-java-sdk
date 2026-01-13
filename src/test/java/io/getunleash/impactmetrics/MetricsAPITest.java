package io.getunleash.impactmetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;
import io.getunleash.variant.Payload;
import io.getunleash.variant.Variant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class MetricsAPITest {

    private VariantResolver fakeVariantResolver(String variantName, boolean featureEnabled) {
        VariantResolver resolver = mock(VariantResolver.class);
        Variant variant =
                new Variant(
                        variantName,
                        (Payload) null,
                        !variantName.equals("disabled"),
                        null,
                        featureEnabled);
        when(resolver.getVariantForImpactMetrics(any(String.class), any(UnleashContext.class)))
                .thenReturn(variant);
        return resolver;
    }

    @Test
    public void should_not_register_a_counter_with_empty_name_or_help() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineCounter("some_name", "");
        verify(fakeRegistry, never()).counter(any(MetricOptions.class));

        api.defineCounter("", "some_help");
        verify(fakeRegistry, never()).counter(any(MetricOptions.class));
    }

    @Test
    public void should_register_a_counter_with_valid_name_and_help() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineCounter("valid_name", "Valid help text");
        verify(fakeRegistry).counter(any(MetricOptions.class));
    }

    @Test
    public void should_not_register_a_gauge_with_empty_name_or_help() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext =
                new ImpactMetricContext(mock(UnleashConfig.class));
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineGauge("some_name", "");
        verify(fakeRegistry, never()).gauge(any(MetricOptions.class));

        api.defineGauge("", "some_help");
        verify(fakeRegistry, never()).gauge(any(MetricOptions.class));
    }

    @Test
    public void should_register_a_gauge_with_valid_name_and_help() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineGauge("valid_name", "Valid help text");
        verify(fakeRegistry).gauge(any(MetricOptions.class));
    }

    @Test
    public void should_increment_counter_with_valid_parameters() {
        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        Counter fakeCounter = mock(Counter.class);

        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);
        when(fakeRegistry.getCounter("valid_counter")).thenReturn(fakeCounter);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        when(impactMetricsContext.getAppName()).thenReturn("my-app");
        when(impactMetricsContext.getEnvironment()).thenReturn("dev");
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        UnleashContext context = UnleashContext.builder().build();
        MetricFlagContext flagContext = new MetricFlagContext(List.of("featureX"), context);
        api.incrementCounter("valid_counter", 5L, flagContext);

        verify(fakeCounter).inc(eq(5L), labelsCaptor.capture());
        Map<String, String> capturedLabels = labelsCaptor.getValue();
        assertThat(capturedLabels)
                .containsEntry("appName", "my-app")
                .containsEntry("environment", "dev")
                .containsEntry("featureX", "enabled");
    }

    @Test
    public void should_set_gauge_with_valid_parameters() {
        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        Gauge fakeGauge = mock(Gauge.class);

        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);
        when(fakeRegistry.getGauge("valid_gauge")).thenReturn(fakeGauge);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        when(impactMetricsContext.getAppName()).thenReturn("my-app");
        when(impactMetricsContext.getEnvironment()).thenReturn("dev");
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("variantY", true), impactMetricsContext);

        UnleashContext context = UnleashContext.builder().build();
        MetricFlagContext flagContext = new MetricFlagContext(List.of("featureY"), context);
        api.updateGauge("valid_gauge", 10L, flagContext);

        verify(fakeGauge).set(eq(10L), labelsCaptor.capture());
        Map<String, String> capturedLabels = labelsCaptor.getValue();
        assertThat(capturedLabels)
                .containsEntry("appName", "my-app")
                .containsEntry("environment", "dev")
                .containsEntry("featureY", "variantY");
    }

    @Test
    public void defining_a_counter_automatically_sets_label_names() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineCounter("test_counter", "Test help text");
        verify(fakeRegistry)
                .counter(
                        argThat(
                                (MetricOptions options) ->
                                        options.getLabelNames()
                                                .equals(
                                                        List.of(
                                                                "featureName",
                                                                "appName",
                                                                "environment"))));
    }

    @Test
    public void defining_a_gauge_automatically_sets_label_names() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("variantX", true), impactMetricsContext);

        api.defineGauge("test_gauge", "Test help text");
        verify(fakeRegistry)
                .gauge(
                        argThat(
                                (MetricOptions options) ->
                                        options.getLabelNames()
                                                .equals(
                                                        List.of(
                                                                "featureName",
                                                                "appName",
                                                                "environment"))));
    }

    @Test
    public void should_not_register_a_histogram_with_empty_name_or_help() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineHistogram("some_name", "", null);
        verify(fakeRegistry, never()).histogram(any(BucketMetricOptions.class));

        api.defineHistogram("", "some_help", null);
        verify(fakeRegistry, never()).histogram(any(BucketMetricOptions.class));
    }

    @Test
    public void should_register_a_histogram_with_valid_name_and_help() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineHistogram("valid_name", "Valid help text", null);
        verify(fakeRegistry).histogram(any(BucketMetricOptions.class));
    }

    @Test
    public void should_observe_histogram_with_valid_parameters() {
        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        Histogram fakeHistogram = mock(Histogram.class);

        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);
        when(fakeRegistry.getHistogram("valid_histogram")).thenReturn(fakeHistogram);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        when(impactMetricsContext.getAppName()).thenReturn("my-app");
        when(impactMetricsContext.getEnvironment()).thenReturn("dev");
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        UnleashContext context = UnleashContext.builder().build();
        MetricFlagContext flagContext = new MetricFlagContext(List.of("featureX"), context);
        api.observeHistogram("valid_histogram", 1.5, flagContext);

        verify(fakeHistogram).observe(eq(1.5), labelsCaptor.capture());
        Map<String, String> capturedLabels = labelsCaptor.getValue();
        assertThat(capturedLabels)
                .containsEntry("appName", "my-app")
                .containsEntry("environment", "dev")
                .containsEntry("featureX", "enabled");
    }

    @Test
    public void defining_a_histogram_automatically_sets_label_names() {
        ImpactMetricRegistry fakeRegistry = mock(ImpactMetricRegistry.class);

        ImpactMetricContext impactMetricsContext = mock(ImpactMetricContext.class);
        MetricsAPIImpl api =
                new MetricsAPIImpl(
                        fakeRegistry, fakeVariantResolver("disabled", true), impactMetricsContext);

        api.defineHistogram("test_histogram", "Test help text", null);
        verify(fakeRegistry)
                .histogram(
                        argThat(
                                (BucketMetricOptions options) ->
                                        options.getLabelNames()
                                                .equals(
                                                        List.of(
                                                                "featureName",
                                                                "appName",
                                                                "environment"))));
    }
}
