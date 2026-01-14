package io.getunleash.impactmetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.getunleash.util.UnleashConfig;
import org.junit.jupiter.api.Test;

public class ImpactMetricsContextTest {

    @Test
    public void resolving_environment_returns_api_key_environment() {
        UnleashConfig config = mock(UnleashConfig.class);
        when(config.getApiKey()).thenReturn("*:production.hashymchashface");

        ImpactMetricContext context = new ImpactMetricContext(config);

        assertThat(context.getEnvironment()).isEqualTo("production");
    }

    @Test
    public void resolving_environment_from_missing_api_key_resolves_to_development_without_error() {
        UnleashConfig config = mock(UnleashConfig.class);
        when(config.getApiKey()).thenReturn(null);

        ImpactMetricContext context = new ImpactMetricContext(config);

        assertThat(context.getEnvironment()).isEqualTo("development");
    }

    @Test
    public void resolving_environment_from_broken_api_key_resolves_to_development_without_error() {
        UnleashConfig config = mock(UnleashConfig.class);
        when(config.getApiKey()).thenReturn("this-is-not-a-valid-api-key");

        ImpactMetricContext context = new ImpactMetricContext(config);

        assertThat(context.getEnvironment()).isEqualTo("development");
    }
}
