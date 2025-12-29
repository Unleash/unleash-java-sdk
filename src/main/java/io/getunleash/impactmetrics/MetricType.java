package io.getunleash.impactmetrics;

import com.google.gson.annotations.SerializedName;

public enum MetricType {
    @SerializedName("counter")
    COUNTER,
    @SerializedName("gauge")
    GAUGE,
    @SerializedName("histogram")
    HISTOGRAM
}
