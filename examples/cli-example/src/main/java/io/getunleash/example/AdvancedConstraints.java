package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedConstraints {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedConstraints.class);

    public static void main(String[] args) throws InterruptedException {
        String callsPerSecondStr = getOrElse("UNLEASH_RPS", "5");
        Integer callsPerSecond = Integer.parseInt(callsPerSecondStr);
        int sleepInterval = (int) ((1.0 / callsPerSecond) * 1000.0);
        String apiToken = getOrElse("UNLEASH_API_TOKEN",
            "*:development.25a06b75248528f8ca93ce179dcdd141aedfb632231e0d21fd8ff349");
        String apiUrl = getOrElse("UNLEASH_API_URL", "https://app.unleash-hosted.com/demo/api");
        UnleashConfig config = UnleashConfig.builder()
            .appName("client-example.advanced.java")
            .customHttpHeader(
                "Authorization",
                apiToken)
            .unleashAPI(apiUrl)
            .instanceId("java-example")
            .synchronousFetchOnInitialisation(true)
            .build();
        LOGGER.info("Connecting to {}, evaluating at {} rps ({} ms interval)",apiUrl, callsPerSecond, sleepInterval);
        Unleash unleash = new DefaultUnleash(config);
        while (true) {
            var results = unleash.more().evaluateAllToggles();
            var toggle = unleash.isEnabled("openai.chat");
            var variant = unleash.getVariant("openai.chat");
            LOGGER.info("Got {} toggles and openai.chat enabled {},  variant name: {}, featureEnabled was {} for openai.chat. sleeping {}ms", results.size(), toggle, variant.getName(), variant.isFeatureEnabled(), sleepInterval);
            Thread.sleep(sleepInterval);
        }
    }

    public static String getOrElse(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
