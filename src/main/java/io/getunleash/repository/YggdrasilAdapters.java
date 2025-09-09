package io.getunleash.repository;

import io.getunleash.DefaultUnleash;
import io.getunleash.UnleashContext;
import io.getunleash.engine.Context;
import io.getunleash.engine.IStrategy;
import io.getunleash.engine.Payload;
import io.getunleash.engine.VariantDef;
import io.getunleash.lang.Nullable;
import io.getunleash.strategy.Strategy;
import io.getunleash.variant.Variant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YggdrasilAdapters {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUnleash.class);

    @NotNull
    public static IStrategy adapt(Strategy s) {
        return new IStrategy() {
            @Override
            public String getName() {
                return s.getName();
            }

            @Override
            public boolean isEnabled(Map<String, String> map, Context context) {
                return s.isEnabled(map, adapt(context));
            }
        };
    }

    public static UnleashContext adapt(Context context) {
        ZonedDateTime currentTime = ZonedDateTime.now();
        if (context.getCurrentTime() != null) {
            try {
                currentTime = ZonedDateTime.parse(context.getCurrentTime());
            } catch (DateTimeParseException e) {
                LOGGER.warn(
                        "Could not parse current time from context, falling back to system time: ",
                        context.getCurrentTime());
                currentTime = ZonedDateTime.now();
            }
        }

        return new UnleashContext(
                context.getAppName(),
                context.getEnvironment(),
                context.getUserId(),
                context.getSessionId(),
                context.getRemoteAddress(),
                currentTime,
                context.getProperties());
    }

    public static Context adapt(UnleashContext context) {
        Context mapped = new Context();
        mapped.setAppName(context.getAppName().orElse(null));
        mapped.setEnvironment(context.getEnvironment().orElse(null));
        mapped.setUserId(context.getUserId().orElse(null));
        mapped.setSessionId(context.getSessionId().orElse(null));
        mapped.setRemoteAddress(context.getRemoteAddress().orElse(null));
        mapped.setProperties(context.getProperties());
        mapped.setCurrentTime(
                DateTimeFormatter.ISO_INSTANT.format(
                        context.getCurrentTime().orElse(ZonedDateTime.now()).toInstant()));
        return mapped;
    }

    public static Variant adapt(Optional<VariantDef> variant, Variant defaultValue) {
        if (!variant.isPresent()) {
            return defaultValue;
        }
        VariantDef unwrapped = variant.get();
        return new Variant(
                unwrapped.getName(),
                adapt(unwrapped.getPayload()),
                unwrapped.isEnabled(),
                unwrapped.isFeatureEnabled());
    }

    public static @Nullable io.getunleash.variant.Payload adapt(@Nullable Payload payload) {
        return Optional.ofNullable(payload)
                .map(p -> new io.getunleash.variant.Payload(p.getType(), p.getValue()))
                .orElse(new io.getunleash.variant.Payload("string", null));
    }
}
