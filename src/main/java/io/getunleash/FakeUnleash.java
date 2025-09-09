package io.getunleash;

import io.getunleash.lang.Nullable;
import io.getunleash.variant.Variant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FakeUnleash implements Unleash {
    private boolean enableAll = false;
    private boolean disableAll = false;

    /**
     * @implNote This uses {@link Queue} instead of {@link List}, as there are concurrent queues,
     *     but no concurrent lists, in the jdk. This will never be drained. Only iterated over.
     */
    private final Map<String, Queue<Predicate<UnleashContext>>> conditionalFeatures =
            new ConcurrentHashMap<>();

    private final Map<String, Boolean> excludedFeatures = new ConcurrentHashMap<>();
    private final Map<String, Boolean> features = new ConcurrentHashMap<>();
    private final Map<String, Variant> variants = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled(
            String toggleName,
            UnleashContext context,
            BiPredicate<String, UnleashContext> fallbackAction) {
        if (enableAll) {
            return excludedFeatures.getOrDefault(toggleName, true);
        } else if (disableAll) {
            return excludedFeatures.getOrDefault(toggleName, false);
        } else {
            Boolean unconditionallyEnabled = features.get(toggleName);
            if (unconditionallyEnabled != null) {
                return unconditionallyEnabled;
            }
            Queue<Predicate<UnleashContext>> conditionalFeaturePredicates =
                    conditionalFeatures.get(toggleName);
            if (conditionalFeaturePredicates == null) {
                return fallbackAction.test(toggleName, context);
            } else {
                return conditionalFeaturePredicates.stream()
                        .anyMatch(
                                conditionalFeaturePredicate ->
                                        conditionalFeaturePredicate.test(context));
            }
        }
    }

    @Override
    public Variant getVariant(String toggleName, UnleashContext context) {
        return getVariant(toggleName, context, Variant.DISABLED_VARIANT);
    }

    @Override
    public Variant getVariant(String toggleName, UnleashContext context, Variant defaultValue) {
        if (isEnabled(toggleName, context)) {
            Variant variant = variants.get(toggleName);
            if (variant != null) {
                return variant;
            }
        }
        return defaultValue;
    }

    @Override
    public Variant getVariant(String toggleName) {
        return getVariant(toggleName, Variant.DISABLED_VARIANT);
    }

    @Override
    public Variant getVariant(String toggleName, Variant defaultValue) {
        return getVariant(toggleName, UnleashContext.builder().build(), defaultValue);
    }

    @Override
    public MoreOperations more() {
        return new FakeMore();
    }

    public void enableAll() {
        disableAll = false;
        enableAll = true;
        features.clear();
        excludedFeatures.clear();
        conditionalFeatures.clear();
    }

    public void enableAllExcept(String... excludedFeatures) {
        enableAll();
        for (String toggle : excludedFeatures) {
            this.excludedFeatures.put(toggle, false);
        }
    }

    public void disableAll() {
        disableAll = true;
        enableAll = false;
        features.clear();
        excludedFeatures.clear();
        conditionalFeatures.clear();
    }

    public void disableAllExcept(String... excludedFeatures) {
        disableAll();
        for (String toggle : excludedFeatures) {
            this.excludedFeatures.put(toggle, true);
        }
    }

    public void resetAll() {
        disableAll = false;
        enableAll = false;
        excludedFeatures.clear();
        features.clear();
        conditionalFeatures.clear();
        variants.clear();
    }

    public void enable(String... features) {
        for (String name : features) {
            this.conditionalFeatures.remove(name);
            this.features.put(name, true);
        }
    }

    public void disable(String... features) {
        for (String name : features) {
            this.conditionalFeatures.remove(name);
            this.features.put(name, false);
        }
    }

    public void reset(String... features) {
        for (String name : features) {
            this.conditionalFeatures.remove(name);
            this.features.remove(name);
        }
    }

    /**
     * Enables or disables feature toggles depending on the evaluation of the {@code
     * contextMatcher}. This can be called multiple times. If <b>any</b> of the context matchers
     * match, the feature is enabled. This lets you conditionally configure multiple different tests
     * to do different things, while running concurrently.
     *
     * <p>This will be overwritten if {@link #enable(String...)} or {@link #disable(String...)} are
     * called and vice versa.
     *
     * @param contextMatcher the context matcher to evaluate
     * @param features the features for which the context matcher will be invoked
     */
    public void conditionallyEnable(Predicate<UnleashContext> contextMatcher, String... features) {
        for (String name : features) {
            // calling conditionallyEnable() should override having called enable() or disable()
            this.features.remove(name);
            this.conditionalFeatures
                    .computeIfAbsent(name, ignored -> new LinkedBlockingQueue<>())
                    .add(contextMatcher);
        }
    }

    public void setVariant(String toggleName, Variant variant) {
        variants.put(toggleName, variant);
    }

    public class FakeMore implements MoreOperations {

        @Override
        public List<String> getFeatureToggleNames() {
            return Stream.concat(features.keySet().stream(), conditionalFeatures.keySet().stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<FeatureDefinition> getFeatureToggleDefinition(String toggleName) {
            if (conditionalFeatures.containsKey(toggleName) || features.containsKey(toggleName)) {
                return Optional.of(
                        new FeatureDefinition(
                                toggleName, Optional.of("experiment"), "default", true));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public List<EvaluatedToggle> evaluateAllToggles() {
            return evaluateAllToggles(null);
        }

        @Override
        public List<EvaluatedToggle> evaluateAllToggles(@Nullable UnleashContext context) {
            return getFeatureToggleNames().stream()
                    .map(
                            toggleName ->
                                    new EvaluatedToggle(
                                            toggleName,
                                            isEnabled(toggleName),
                                            getVariant(toggleName)))
                    .collect(Collectors.toList());
        }
    }
}
