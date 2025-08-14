package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;

public class AdvancedConstraints {

    public static void main(String[] args) throws InterruptedException {
        UnleashConfig config = UnleashConfig.builder()
                .appName("client-example.advanced.java")
                .customHttpHeader(
                        "Authorization",
                        getOrElse("UNLEASH_API_TOKEN",
                                "*:development.25a06b75248528f8ca93ce179dcdd141aedfb632231e0d21fd8ff349"))
                .unleashAPI(getOrElse("UNLEASH_API_URL", "https://app.unleash-hosted.com/demo/api"))
                .instanceId("java-example")
                .synchronousFetchOnInitialisation(true)
                .sendMetricsInterval(30).build();

        Unleash unleash = new DefaultUnleash(config);
        UnleashContext context = UnleashContext.builder()
            .addProperty("semver", "1.5.2")
            .build();
        UnleashContext smallerSemver = UnleashContext.builder()
            .addProperty("semver", "1.1.0")
            .build();
        while (true) {
                    unleash.isEnabled("advanced.constraints", context); // expect this to be true
                    unleash.isEnabled("advanced.constraints", smallerSemver); // expect this to be false
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
