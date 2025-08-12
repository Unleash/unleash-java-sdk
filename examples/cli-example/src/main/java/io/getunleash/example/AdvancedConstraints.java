package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;
import java.util.Random;

public class AdvancedConstraints {
    public static String toString(UnleashContext context) {
        String appName = context.getAppName().orElse("N/A");
        String environment = context.getEnvironment().orElse("N/A");
        String userId = context.getUserId().orElse("N/A");
        String sessionId = context.getSessionId().orElse("N/A");
        String properties = context.getProperties().isEmpty() ? "N/A" : context.getProperties().toString();
        return "UnleashContext{" +
            "appName=" + appName +
            ", environment=" + environment +
            ", userId=" + userId +
            ", sessionId=" + sessionId +
            ", properties=" + properties +
            '}';
    }

    public static void main(String[] args) throws InterruptedException {
        String prodToken = "java-sdk:production.fda773f8736e033f33159dbadb7327ab562bad51c4e87daa3095849c";
        String devToken = "*:development.25a06b75248528f8ca93ce179dcdd141aedfb632231e0d21fd8ff349";
        String env = "production"; // Change to "production" for production environment
        String token = env.equals("production") ? prodToken : devToken;
        UnleashConfig config = UnleashConfig.builder()
                .appName("client-example.advanced.java")
                .customHttpHeader(
                        "Authorization",
                        getOrElse("UNLEASH_API_TOKEN",
                                token))
                .unleashAPI(getOrElse("UNLEASH_API_URL", "https://app.unleash-hosted.com/demo/api"))
                .instanceId("java-example")
                .synchronousFetchOnInitialisation(true)
                .sendMetricsInterval(30).build();

        Unleash unleash = new DefaultUnleash(config);

        String[] companyIdOptions = {"abc123", "xyz789", "def456"};
        for (int i = 1; i <= 10; i++) {
            UnleashContext context = UnleashContext.builder()
                .addProperty("providerName", "es-barcelona")
                .addProperty("company_id", companyIdOptions[new Random().nextInt(companyIdOptions.length)])
                .userId("user-" + i)
                .sessionId("session-" + new Random().nextInt(1000, 9999))
                .environment(env)
                .build();

            boolean resultContext = unleash.isEnabled("advanced.constraints-java", context);

            System.out.println("Iteration " + i);
            System.out.println("Context: " + toString(context));
            System.out.println("  Enabled? " + resultContext);
            System.out.println("----------------------------");

            unleash.more().evaluateAllToggles(context).forEach(toggle -> {
                System.out.println("Feature: " + toggle.getName());
                System.out.println("  Enabled? " + toggle.isEnabled());
                System.out.println("  Variant: " + toggle.getVariant());
            });
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
