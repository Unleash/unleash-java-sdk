package io.getunleash.repository;

import io.getunleash.FeatureDefinition;
import io.getunleash.UnleashContext;
import io.getunleash.engine.FlatResponse;
import io.getunleash.engine.VariantDef;
import io.getunleash.lang.Nullable;
import java.util.stream.Stream;

public interface FeatureRepository {

    @Nullable
    FlatResponse<Boolean> isEnabled(String toggleName, UnleashContext context);

    @Nullable
    FlatResponse<VariantDef> getVariant(String toggleName, UnleashContext context);

    Stream<FeatureDefinition> listKnownToggles();

    void shutdown();
}
