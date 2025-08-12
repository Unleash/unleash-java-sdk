package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;
import java.util.Random;
import java.util.stream.IntStream;

public class AdvancedConstraints {
    public static String contextToString(UnleashContext context) {
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

    private static String limitTo(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    public static void main(String[] args) throws InterruptedException {
        String feature = "advanced.constraints-java";
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


        // Define scenarios to test
        String[] providerNameOptions = {
            "es-barcelona",    // matching prefix
            "es-",             // prefix only
            "es",              // shorter than prefix
            "",                // empty string
            null,              // null providerName
            "ES-barcelona",    // mixed case
            "en-barcelona",    // non‑matching prefix
            "es-🔧🔧🔧",       // emojis
            "es-" + "a".repeat(5000) // long string
        };

        String[] companyIdOptions = {
            "abc123",
            "xyz789",
            "def456",
            "",                // empty company_id
            null,               // null company_id
            "abc123-🔧🔧🔧",       // emojis
            "abc123-" + "x".repeat(5000) // long string
        };
        int iteration = 1;
        for (String providerName : providerNameOptions) {
            for (String companyId : companyIdOptions) {
                // Build the context; only set providerName or company_id if not null
                UnleashContext.Builder ctxBuilder = UnleashContext.builder()
                    .userId("user-" + iteration)
                    .sessionId("session-" + iteration)
                    .environment(env);

                ctxBuilder.addProperty("providerName", providerName);
                ctxBuilder.addProperty("company_id", companyId);

                UnleashContext context = ctxBuilder.build();
                boolean enabled = unleash.isEnabled(feature, context);

                System.out.println("Iteration " + iteration++);
                System.out.println("Context: " + contextToString(context));
                System.out.println("    providerName: " + providerName +
                    ", company_id: " + companyId +
                    " => Enabled? " + enabled);
                System.out.println("----------------------------");
            }
        }

        IntStream.range(0, 100000).parallel().forEach(i -> {
            UnleashContext ctx = UnleashContext.builder()
                .addProperty("providerName", providerNameOptions[i % providerNameOptions.length])
                .userId("user-" + i)
                .addProperty("company_id", companyIdOptions[i % companyIdOptions.length]) // Vary company_id for each user
                .environment(env)
                .build();
            boolean enabled = unleash.isEnabled(feature, ctx);
            System.out.println("Parallel Iteration " + i +
                " => providerName: " + limitTo(providerNameOptions[i % providerNameOptions.length], 20) +
                ", company_id: " + limitTo(companyIdOptions[i % companyIdOptions.length], 20) +
                ", userId: user-" + i +
                " => Enabled? " + enabled);
        });
    }

    public static String getOrElse(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
