package io.getunleash.repository;

import io.getunleash.FeatureDefinition;
import io.getunleash.UnleashContext;
import io.getunleash.engine.WasmIsEnabledResponse;
import io.getunleash.engine.WasmVariantResponse;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public interface FeatureRepository {

    @Nullable
    WasmIsEnabledResponse isEnabled(String toggleName, UnleashContext context);

    @Nullable
    WasmVariantResponse getVariant(String toggleName, UnleashContext context);

    Stream<FeatureDefinition> listKnownToggles();

    void shutdown();
}
