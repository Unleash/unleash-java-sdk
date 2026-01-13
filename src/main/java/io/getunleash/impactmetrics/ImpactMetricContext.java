package io.getunleash.impactmetrics;

import io.getunleash.util.UnleashConfig;

public class ImpactMetricContext {
    private final UnleashConfig config;

    public ImpactMetricContext(UnleashConfig config) {
        this.config = config;
    }

    public String getAppName() {
        return config.getAppName();
    }

    private static String extractEnvironment(String apiKey) {
        try {
            int colon = apiKey.indexOf(':');
            int dot = apiKey.indexOf('.', colon + 1);
            return apiKey.substring(colon + 1, dot);
        } catch (RuntimeException e) {
            // Should never happen but gotta pick something if it does
            // if 'development' safe? 'default' definitely isn't these days
            return "development";
        }
    }

    public String getEnvironment() {
        return extractEnvironment(config.getApiKey());
    }
}
