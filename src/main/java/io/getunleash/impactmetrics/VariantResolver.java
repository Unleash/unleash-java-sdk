package io.getunleash.impactmetrics;

import io.getunleash.UnleashContext;
import io.getunleash.variant.Variant;

public interface VariantResolver {
    Variant forceGetVariant(String flagName, UnleashContext context);
}
