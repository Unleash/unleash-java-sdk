package io.getunleash.impactmetrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class HistogramBucketSerializer implements JsonSerializer<HistogramBucket> {

    @Override
    public JsonElement serialize(
            HistogramBucket src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        if (Double.isInfinite(src.getLe()) && src.getLe() > 0) {
            jsonObject.addProperty("le", "+Inf");
        } else {
            jsonObject.addProperty("le", src.getLe());
        }
        jsonObject.addProperty("count", src.getCount());
        return jsonObject;
    }
}
