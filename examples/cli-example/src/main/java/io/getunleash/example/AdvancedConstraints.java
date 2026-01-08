package io.getunleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.event.UnleashReady;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.util.UnleashConfig;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class AdvancedConstraints {
    public static void main(String[] args) throws InterruptedException {
        AtomicBoolean isReady = new AtomicBoolean(false);
        UnleashSubscriber subscriber =
                new UnleashSubscriber() {
                    @Override
                    public void onReady(UnleashReady unleashReady) {
                        isReady.set(true);
                    }
                };
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("client-example.advanced")
                        .customHttpHeader(
                                "Authorization",
                                "default:production.6036005ef63f6af528dbb759ddcad11323f6ca8c54940c395445d736")
                        .unleashAPI("https://eu.app.unleash-hosted.com/demo/api/")
                        .instanceId("example")
                    .fetchTogglesInterval(1)
                        .subscriber(subscriber)
                        .build();
        Unleash unleash = new DefaultUnleash(config);
        List<String> options = Arrays.asList("press-print", "press-deal", "press-exa");
        AtomicInteger optionIndex = new AtomicInteger(0);
        Supplier<String> optionSupplier =
                () -> options.get(optionIndex.getAndUpdate(i -> (i + 1) % options.size()));

        //Thread.sleep(1000);
        String flag = "validation-promotions-int";
        for (int i = 0; i < 1000000; i++) {
            boolean isUnleashReady = isReady.get();
            String releaseGroup = optionSupplier.get();
            boolean enabled =
                    unleash.isEnabled(
                            flag,
                            UnleashContext.builder().addProperty("release_group", releaseGroup).build());
            boolean disabled =
                    unleash.isEnabled(
                            flag,
                            UnleashContext.builder()
                                    .addProperty("release_group", "something-else")
                                    .build());
            boolean matchesExpectation = enabled && !disabled;
            System.out.printf(
                    "Ready=%s, enabled(%s)=%s, disabled=%s, matches=%s%n",
                    isUnleashReady, releaseGroup, enabled, disabled, matchesExpectation);
            if (isUnleashReady && !matchesExpectation) {
                throw new IllegalStateException(
                        "Unexpected evaluation results after readiness.");
            }
        }

    }
}
