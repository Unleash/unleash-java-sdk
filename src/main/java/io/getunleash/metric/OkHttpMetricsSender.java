package io.getunleash.metric;

import static io.getunleash.util.UnleashConfig.UNLEASH_INTERVAL;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.getunleash.UnleashException;
import io.getunleash.event.EventDispatcher;
import io.getunleash.util.AtomicLongSerializer;
import io.getunleash.util.DateTimeSerializer;
import io.getunleash.util.OkHttpClientConfigurer;
import io.getunleash.util.UnleashConfig;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpMetricsSender implements MetricSender {
    private final UnleashConfig config;
    private final MediaType JSON =
            Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));

    private final EventDispatcher eventDispatcher;
    private final OkHttpClient client;

    private final Gson gson;

    private final HttpUrl clientRegistrationUrl;

    private final HttpUrl clientMetricsUrl;

    public OkHttpMetricsSender(UnleashConfig config) {
        this.config = config;
        this.clientMetricsUrl =
                Objects.requireNonNull(HttpUrl.get(config.getUnleashURLs().getClientMetricsURL()));
        this.clientRegistrationUrl =
                Objects.requireNonNull(HttpUrl.get(config.getUnleashURLs().getClientRegisterURL()));
        this.eventDispatcher = new EventDispatcher(config);

        OkHttpClient.Builder builder;
        if (config.getProxy() != null) {
            builder = new OkHttpClient.Builder().proxy(config.getProxy());
        } else {
            builder = new OkHttpClient.Builder();
        }
        builder =
                builder.callTimeout(config.getSendMetricsConnectTimeout())
                        .readTimeout(config.getSendMetricsReadTimeout());
        this.client = OkHttpClientConfigurer.configureInterceptor(config, builder.build());

        this.gson =
                new GsonBuilder()
                        .registerTypeAdapter(LocalDateTime.class, new DateTimeSerializer())
                        .registerTypeAdapter(AtomicLong.class, new AtomicLongSerializer())
                        .create();
    }

    @Override
    public int registerClient(ClientRegistration registration) {
        if (!config.isDisableMetrics()) {
            try {
                int statusCode = post(clientRegistrationUrl, registration);
                eventDispatcher.dispatch(registration);
                return statusCode;
            } catch (UnleashException ex) {
                eventDispatcher.dispatch(ex);
            }
        }
        return -1;
    }

    @Override
    public int sendMetrics(ClientMetrics metrics) {
        if (!config.isDisableMetrics()) {
            try {
                post(clientMetricsUrl, metrics);
                eventDispatcher.dispatch(metrics);
            } catch (UnleashException ex) {
                eventDispatcher.dispatch(ex);
            }
        }
        return -1;
    }

    private int post(HttpUrl url, Object o) {
        RequestBody body = RequestBody.create(gson.toJson(o), JSON);
        Request request =
                new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader(UNLEASH_INTERVAL, config.getSendMetricsIntervalMillis())
                        .build();
        try (Response response = this.client.newCall(request).execute()) {
            return response.code();
        } catch (IOException ioEx) {
            throw new UnleashException("Could not post to Unleash API", ioEx);
        }
    }
}
