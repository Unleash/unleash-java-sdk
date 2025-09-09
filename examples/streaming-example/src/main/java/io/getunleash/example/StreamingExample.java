package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashException;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.util.UnleashConfig;

public class StreamingExample {

    private static Unleash unleash;

    private static class StreamingEventSubscriber implements UnleashSubscriber {
        @Override
        public void togglesFetched(ClientFeaturesResponse toggleResponse) {
            System.out.println("[STREAMING EVENT] Features updated");
            if (unleash != null) {
                boolean isEnabled = unleash.isEnabled("streaming_flag");
                System.out.println("  streaming_flag: " + (isEnabled ? "ENABLED" : "DISABLED"));
            }
        }

        @Override
        public void onError(UnleashException unleashException) {
            System.err.println("[STREAMING ERROR] " + unleashException.getMessage());
        }
    }

    public static void main(String[] args) {
        StreamingEventSubscriber subscriber = new StreamingEventSubscriber();

        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("streaming-example")
                        .instanceId("streaming-example-instance")
                        .unleashAPI(getOrElse("UNLEASH_API_URL", "https://app.unleash-hosted.com/demo/api"))
                        .customHttpHeader(
                                "Authorization",
                                getOrElse("UNLEASH_API_TOKEN",
                                        "*:development.25a06b75248528f8ca93ce179dcdd141aedfb632231e0d21fd8ff349"))
                        .experimentalStreamingMode()
                        .subscriber(subscriber)
                        .build();

        unleash = new DefaultUnleash(config);

        System.out.println("Streaming client started. Waiting for events... (Press Ctrl+C to exit)");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Streaming example shutting down");
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