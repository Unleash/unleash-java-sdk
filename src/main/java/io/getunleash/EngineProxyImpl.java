package io.getunleash;

import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.WasmIsEnabledResponse;
import io.getunleash.engine.WasmVariantResponse;
import io.getunleash.lang.Nullable;
import io.getunleash.metric.UnleashMetricService;
import io.getunleash.metric.UnleashMetricServiceImpl;
import io.getunleash.repository.FeatureRepositoryImpl;
import io.getunleash.repository.YggdrasilAdapters;
import io.getunleash.strategy.Strategy;
import io.getunleash.util.UnleashConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EngineProxyImpl implements EngineProxy {

    UnleashEngine unleashEngine;
    FeatureRepositoryImpl featureRepository;
    UnleashMetricService metricService;

    public EngineProxyImpl(UnleashConfig unleashConfig, Strategy... strategies) {
        Map<String, Strategy> strategyMap = buildStrategyMap(strategies);

        this.unleashEngine =
                new UnleashEngine(
                        strategyMap.values().stream()
                                .map(YggdrasilAdapters::adapt)
                                .collect(Collectors.toList()),
                        Optional.ofNullable(unleashConfig.getFallbackStrategy())
                                .map(YggdrasilAdapters::adapt)
                                .orElse(null));

        this.featureRepository = new FeatureRepositoryImpl(unleashConfig, unleashEngine);
        this.metricService =
                new UnleashMetricServiceImpl(
                        unleashConfig, unleashConfig.getScheduledExecutor(), this.unleashEngine);

        metricService.register(strategyMap.keySet());
    }

    @Override
    @Nullable
    public WasmIsEnabledResponse isEnabled(String toggleName, UnleashContext context) {
        return this.featureRepository.isEnabled(toggleName, context);
    }

    @Override
    @Nullable
    public WasmVariantResponse getVariant(String toggleName, UnleashContext context) {
        return this.featureRepository.getVariant(toggleName, context);
    }

    @Override
    public void register(Set<String> strategies) {
        this.metricService.register(strategies);
    }

    @Override
    public Stream<FeatureDefinition> listKnownToggles() {
        return this.featureRepository.listKnownToggles();
    }

    @Override
    public void shutdown() {
        this.featureRepository.shutdown();
    }

    private static Map<String, Strategy> buildStrategyMap(@Nullable Strategy[] strategies) {
        Map<String, Strategy> map = new HashMap<>();

        if (strategies != null) {
            for (Strategy strategy : strategies) {
                map.put(strategy.getName(), strategy);
            }
        }

        return map;
    }
}
