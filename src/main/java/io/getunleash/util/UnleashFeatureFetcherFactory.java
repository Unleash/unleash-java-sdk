package io.getunleash.util;

import io.getunleash.repository.FeatureSource;
import java.util.function.Function;

public interface UnleashFeatureFetcherFactory extends Function<UnleashConfig, FeatureSource> {}
