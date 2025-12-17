package io.getunleash.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.getunleash.impactmetrics.HistogramBucket;
import org.junit.jupiter.api.Test;

public class HistogramBucketSerializerTest {

    private final Gson gson =
            new GsonBuilder()
                    .registerTypeAdapter(HistogramBucket.class, new HistogramBucketSerializer())
                    .create();

    @Test
    public void should_serialize_positive_infinity_as_plus_inf_string() {
        HistogramBucket bucket = new HistogramBucket(Double.POSITIVE_INFINITY, 5L);

        String json = gson.toJson(bucket);

        assertThat(json).contains("\"le\":\"+Inf\"");
        assertThat(json).contains("\"count\":5");
    }

    @Test
    public void should_serialize_regular_number_as_number() {
        HistogramBucket bucket = new HistogramBucket(10.5, 3L);

        String json = gson.toJson(bucket);

        assertThat(json).contains("\"le\":10.5");
        assertThat(json).contains("\"count\":3");
    }

    @Test
    public void should_serialize_zero_as_number() {
        HistogramBucket bucket = new HistogramBucket(0.0, 1L);

        String json = gson.toJson(bucket);

        assertThat(json).contains("\"le\":0.0");
        assertThat(json).contains("\"count\":1");
    }
}
