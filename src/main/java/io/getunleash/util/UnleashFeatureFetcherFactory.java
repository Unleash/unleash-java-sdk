package io.getunleash.util;

import io.getunleash.repository.FeatureFetcher;
import java.util.function.Function;

public interface UnleashFeatureFetcherFactory extends Function<UnleashConfig, FeatureFetcher> {}
