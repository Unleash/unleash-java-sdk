package io.getunleash.strategies.custom;

import io.getunleash.UnleashContext;
import io.getunleash.strategy.Strategy;
import java.util.Map;

public class ConstraintEvaluatorCustom implements Strategy {
    @Override
    public String getName() {
        return "repeated";
    }

    @Override
    public boolean isEnabled(Map<String, String> parameters, UnleashContext context) {
        return context.getProperties().get("myFancy").equals(parameters.get("myFancy"));
    }
}
