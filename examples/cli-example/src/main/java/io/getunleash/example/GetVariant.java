package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;

import static io.getunleash.example.AdvancedConstraints.getOrElse;

public class GetVariant {
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
            unleash.getVariant("advanced.constraints"); // expect this to be true
            unleash.getVariant("advanced.constraints", smallerSemver); // expect this to be false
            Thread.sleep(1);
        }
    }
}
