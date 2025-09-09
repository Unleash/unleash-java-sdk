package io.getunleash.metric;

import static io.getunleash.util.UnleashConfig.UNLEASH_INTERVAL;

import com.google.gson.*;
import io.getunleash.UnleashException;
import io.getunleash.event.EventDispatcher;
import io.getunleash.util.AtomicLongSerializer;
import io.getunleash.util.DateTimeSerializer;
import io.getunleash.util.InstantSerializer;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashURLs;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultHttpMetricsSender implements MetricSender {

    private final Gson gson;
    private final EventDispatcher eventDispatcher;
    private UnleashConfig unleashConfig;
    private final URL clientRegistrationURL;
    private final URL clientMetricsURL;

    public DefaultHttpMetricsSender(UnleashConfig unleashConfig) {
        this.unleashConfig = unleashConfig;
        this.eventDispatcher = new EventDispatcher(unleashConfig);
        UnleashURLs urls = unleashConfig.getUnleashURLs();
        this.clientMetricsURL = urls.getClientMetricsURL();
        this.clientRegistrationURL = urls.getClientRegisterURL();

        this.gson =
                new GsonBuilder()
                        .registerTypeAdapter(LocalDateTime.class, new DateTimeSerializer())
                        .registerTypeAdapter(Instant.class, new InstantSerializer())
                        .registerTypeAdapter(AtomicLong.class, new AtomicLongSerializer())
                        .create();
    }

    public int registerClient(ClientRegistration registration) {
        if (!unleashConfig.isDisableMetrics()) {
            try {
                int statusCode = post(clientRegistrationURL, registration);
                eventDispatcher.dispatch(registration);
                return statusCode;
            } catch (UnleashException ex) {
                eventDispatcher.dispatch(ex);
                return -1;
            }
        }
        return -1;
    }

    public int sendMetrics(ClientMetrics metrics) {
        if (!unleashConfig.isDisableMetrics() && metrics.getBucket() != null) {
            try {
                int statusCode = post(clientMetricsURL, metrics);
                eventDispatcher.dispatch(metrics);
                return statusCode;
            } catch (UnleashException ex) {
                eventDispatcher.dispatch(ex);
                return -1;
            }
        }
        return -1;
    }

    private int post(URL url, Object o) throws UnleashException {

        HttpURLConnection connection = null;
        try {
            if (this.unleashConfig.getProxy() != null) {
                connection = (HttpURLConnection) url.openConnection(this.unleashConfig.getProxy());
            } else {
                connection = (HttpURLConnection) url.openConnection();
            }
            connection.setConnectTimeout(
                    (int) unleashConfig.getSendMetricsConnectTimeout().toMillis());
            connection.setReadTimeout((int) unleashConfig.getSendMetricsReadTimeout().toMillis());
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty(
                    UNLEASH_INTERVAL, this.unleashConfig.getSendMetricsIntervalMillis());
            UnleashConfig.setRequestProperties(connection, this.unleashConfig);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
            gson.toJson(o, wr);
            wr.flush();
            wr.close();

            connection.connect();

            // TODO should probably check response code to detect errors?
            return connection.getResponseCode();
        } catch (IOException e) {
            throw new UnleashException("Could not post to Unleash API", e);
        } catch (IllegalStateException e) {
            throw new UnleashException(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
