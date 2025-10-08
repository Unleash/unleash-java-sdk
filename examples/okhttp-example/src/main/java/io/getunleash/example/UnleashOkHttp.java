package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.UnleashException;
import io.getunleash.event.UnleashReady;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.repository.OkHttpFeatureFetcher;
import io.getunleash.util.UnleashConfig;

public class UnleashOkHttp {
    public static void main(String[] args) throws InterruptedException {

        UnleashConfig config = UnleashConfig.builder().appName("client-example.okhttp")
                .customHttpHeader("Authorization",
                    getOrElse("UNLEASH_API_TOKEN",
                            "*:development.25a06b75248528f8ca93ce179dcdd141aedfb632231e0d21fd8ff349"))
                .unleashAPI(getOrElse("UNLEASH_API_URL", "https://app.unleash-hosted.com/demo/api"))
                .unleashFeatureFetcherFactory(OkHttpFeatureFetcher::new)
                .fetchTogglesInterval(10)
                .synchronousFetchOnInitialisation(true)
                .build();
        Unleash unleash = new DefaultUnleash(config);
        unleash.more().getFeatureToggleNames().forEach(t -> System.out.println(t));
        while (true) {
            Thread.sleep(5000);
            System.out.println(unleash.isEnabled("my.feature",
                    UnleashContext.builder().addProperty("email", "test@getunleash.ai").build()));
            System.out.println(unleash.getVariant("my.feature"));
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
