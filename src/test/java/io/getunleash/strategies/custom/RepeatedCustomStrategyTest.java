package io.getunleash.strategies.custom;

import static org.assertj.core.api.Assertions.assertThat;

import io.getunleash.DefaultUnleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.ResourceReader;
import io.getunleash.util.UnleashConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class RepeatedCustomStrategyTest {
    private String loadMockFeatures(String path) {
        return ResourceReader.readResourceAsString(path);
    }

    @Test
    public void
            repeated_custom_strategy_evaluates_to_true_if_any_custom_strategy_evaluates_to_true() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .unleashAPI("http://test:4242")
                        .appName("multiple_connection")
                        .instanceId("repeated_custom_strategy")
                        .toggleBootstrapProvider(
                                () ->
                                        Optional.of(
                                                loadMockFeatures("repeated_custom_strategy.json")))
                        .build();
        var myUnleash = new DefaultUnleash(config, new ConstraintEvaluatorCustom());
        UnleashContext context = UnleashContext.builder().addProperty("myFancy", "one").build();
        assertThat(myUnleash.isEnabled("repeated.custom", context)).isTrue();
    }
}
