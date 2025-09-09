package io.getunleash.repository;

import io.getunleash.FeatureDefinition;
import io.getunleash.UnleashContext;
import io.getunleash.engine.VariantDef;
import io.getunleash.engine.WasmResponse;
import java.util.stream.Stream;

public interface FeatureRepository {

    WasmResponse<Boolean> isEnabled(String toggleName, UnleashContext context);

    WasmResponse<VariantDef> getVariant(String toggleName, UnleashContext context);

    Stream<FeatureDefinition> listKnownToggles();

    void shutdown();
}
