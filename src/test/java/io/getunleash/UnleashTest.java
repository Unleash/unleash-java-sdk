package io.getunleash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.getunleash.engine.VariantDef;
import io.getunleash.engine.WasmResponse;
import io.getunleash.repository.ToggleBootstrapProvider;
import io.getunleash.strategy.Strategy;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import io.getunleash.variant.Variant;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UnleashTest {

    private UnleashContextProvider contextProvider;
    private UnleashConfig baseConfig;

    private UnleashConfig.Builder createConfigBuilder() {
        return new UnleashConfig.Builder()
                .appName("test")
                .environment("test")
                .unleashAPI("http://localhost:4242/api/")
                .disableMetrics()
                .disablePolling()
                .scheduledExecutor(mock(UnleashScheduledExecutor.class));
    }

    @BeforeEach
    public void setup() {

        contextProvider = mock(UnleashContextProvider.class);
        when(contextProvider.getContext()).thenReturn(UnleashContext.builder().build());

        baseConfig = createConfigBuilder().unleashContextProvider(contextProvider).build();
    }

    @Test
    public void unknown_feature_should_use_default_setting() {
        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(eq("test"), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<Boolean>(false, null));
        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        boolean featureEnabled = unleash.isEnabled("test", true);

        assertThat(featureEnabled).isTrue();
    }

    @Test
    public void fallback_function_should_be_invoked_and_return_true() {
        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(eq("test"), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<Boolean>(false, null));
        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        BiPredicate<String, UnleashContext> fallbackAction = mock(BiPredicate.class);
        when(fallbackAction.test(eq("test"), any(UnleashContext.class))).thenReturn(true);

        assertThat(unleash.isEnabled("test", fallbackAction)).isTrue();
        verify(fallbackAction, times(1)).test(anyString(), any(UnleashContext.class));
    }

    @Test
    public void fallback_function_should_be_invoked_also_with_context() {
        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(eq("test"), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<Boolean>(false, null));
        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        BiPredicate<String, UnleashContext> fallbackAction = mock(BiPredicate.class);
        when(fallbackAction.test(eq("test"), any(UnleashContext.class))).thenReturn(true);

        UnleashContext context = UnleashContext.builder().userId("123").build();

        assertThat(unleash.isEnabled("test", context, fallbackAction)).isTrue();
        verify(fallbackAction, times(1)).test(anyString(), any(UnleashContext.class));
    }

    @Test
    void fallback_function_should_be_invoked_and_return_false() {
        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(eq("test"), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<Boolean>(false, null));
        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        BiPredicate<String, UnleashContext> fallbackAction = mock(BiPredicate.class);
        when(fallbackAction.test(eq("test"), any(UnleashContext.class))).thenReturn(false);

        assertThat(unleash.isEnabled("test", fallbackAction)).isFalse();
        verify(fallbackAction, times(1)).test(anyString(), any(UnleashContext.class));
    }

    @Test
    void fallback_function_should_not_be_called_when_toggle_is_defined() {
        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(eq("test"), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<Boolean>(true, true));
        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        BiPredicate<String, UnleashContext> fallbackAction = mock(BiPredicate.class);
        when(fallbackAction.test(eq("test"), any(UnleashContext.class))).thenReturn(false);

        assertThat(unleash.isEnabled("test", fallbackAction)).isTrue();
        verify(fallbackAction, never()).test(anyString(), any(UnleashContext.class));
    }

    @Test
    public void should_register_custom_strategies() {
        // custom strategy
        Strategy customStrategy = mock(Strategy.class);
        when(customStrategy.getName()).thenReturn("custom strategy");
        when(customStrategy.isEnabled(any(), any(UnleashContext.class))).thenReturn(true);

        ToggleBootstrapProvider bootstrapper =
                new ToggleBootstrapProvider() {
                    @Override
                    public Optional<String> read() {
                        return Optional.of(
                                "{\"version\":1,\"features\":[{\"name\":\"test\",\"enabled\":true,\"strategies\":[{\"name\":\"custom strategy\"}]}]}");
                    }
                };

        UnleashConfig config =
                new UnleashConfig.Builder()
                        .appName("test")
                        .toggleBootstrapProvider(bootstrapper)
                        .unleashAPI("http://localhost:4242/api/")
                        .build();

        Unleash unleash = new DefaultUnleash(config, customStrategy);
        boolean result = unleash.isEnabled("test");
        assertThat(result).isTrue();

        verify(customStrategy, times(1)).isEnabled(any(), any(UnleashContext.class));
    }

    @Test
    public void should_support_context_provider() {
        UnleashContext context = UnleashContext.builder().userId("111").build();
        when(contextProvider.getContext()).thenReturn(context);

        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(
                        eq("test"),
                        argThat(UnleashContext -> "111".equals(context.getUserId().orElse(null)))))
                .thenReturn(new WasmResponse<Boolean>(true, true));

        UnleashConfig config =
                createConfigBuilder().unleashContextProvider(contextProvider).build();

        Unleash unleash = new DefaultUnleash(config, engineProxy);

        assertThat(unleash.isEnabled("test")).isTrue();
    }

    @Test
    public void should_support_context_as_part_of_is_enabled_call() {
        UnleashContext context = UnleashContext.builder().userId("13").build();

        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(
                        eq("test"),
                        argThat(UnleashContext -> "13".equals(context.getUserId().orElse(null)))))
                .thenReturn(new WasmResponse<Boolean>(true, true));

        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        assertThat(unleash.isEnabled("test", context)).isTrue();
    }

    @Test
    public void should_support_context_as_part_of_is_enabled_call_and_use_default() {
        UnleashContext context = UnleashContext.builder().userId("13").build();

        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.isEnabled(any(String.class), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<Boolean>(false, null));

        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);

        assertThat(unleash.isEnabled("test", context, true)).isTrue();
    }

    @Test
    public void get_default_variant_when_disabled() {
        UnleashContext context = UnleashContext.builder().userId("1").build();

        Variant variant = new Variant("Chuck", "Norris", true);
        EngineProxy engineProxy = mock(EngineProxy.class);
        when(engineProxy.getVariant(any(String.class), any(UnleashContext.class)))
                .thenReturn(new WasmResponse<VariantDef>(true, null));

        Unleash unleash = new DefaultUnleash(baseConfig, engineProxy);
        Variant result = unleash.getVariant("test", context, variant);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Chuck");
    }
}
