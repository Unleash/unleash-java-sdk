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
            System.out.println("[STREAMING EVENT] Features updated from streaming connection");
            System.out.println("  Response status: " + toggleResponse.getStatus());

            if (toggleResponse.getClientFeatures().isPresent()) {
                System.out.println(
                        "  Raw streaming data: " + toggleResponse.getClientFeatures().get());
            } else {
                System.out.println("  Raw streaming data: None");
            }

            if (unleash != null) {
                boolean isEnabled = unleash.isEnabled("streaming_flag");
                System.out.println(
                        "  Current 'streaming_flag' status: "
                                + (isEnabled ? "ENABLED" : "DISABLED"));
            }
            System.out.println("---");
        }

        @Override
        public void onError(UnleashException unleashException) {
            System.err.println("[STREAMING ERROR] Error in streaming connection:");
            System.err.println("  Message: " + unleashException.getMessage());
            if (unleashException.getCause() != null) {
                System.err.println("  Cause: " + unleashException.getCause().getMessage());
            }
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

        System.out.println("Unleash streaming client started");
        System.out.println("Streaming mode: " + config.isStreamingMode());
        System.out.println("Subscriber registered for streaming events and errors");
        System.out.println("Waiting for streaming events... (Press Ctrl+C to exit)");

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