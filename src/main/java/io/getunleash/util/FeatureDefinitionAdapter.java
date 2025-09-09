package io.getunleash.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.getunleash.FeatureDefinition;
import java.io.IOException;
import java.util.Optional;

public class FeatureDefinitionAdapter extends TypeAdapter<FeatureDefinition> {

    @Override
    public void write(JsonWriter out, FeatureDefinition value) throws IOException {
        out.beginObject();
        out.name("name").value(value.getName());
        out.name("project").value(value.getProject());
        out.name("type").value(value.getType().orElse(null));
        out.name("enabled").value(value.environmentEnabled());
        out.endObject();
    }

    @Override
    public FeatureDefinition read(JsonReader in) throws IOException {
        String name = null;
        String project = null;
        Optional<String> type = Optional.empty();
        boolean enabled = false;

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "name":
                    name = in.nextString();
                    break;
                case "project":
                    project = in.nextString();
                    break;
                case "type":
                    type = Optional.of(in.nextString());
                    break;
                case "enabled":
                    enabled = in.nextBoolean();
                    break;
                default:
                    in.skipValue();
            }
        }
        in.endObject();

        if (name == null) {
            throw new IOException("Missing required field 'name'");
        }

        return new FeatureDefinition(name, type, project, enabled);
    }
}
