package io.getunleash.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.getunleash.FeatureDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ClientFeaturesParserTest {

    @Test
    public void test_basic_parse() {
        String basicFeatures =
                "{\"features\":[{\"name\":\"featureX\",\"project\":\"default\",\"enabled\":true,\"strategies\":[{\"name\":\"default\"}]}]}";
        List<FeatureDefinition> parsed = ClientFeaturesParser.parse(basicFeatures);

        assertEquals(1, parsed.size());

        FeatureDefinition feature = parsed.get(0);

        assertEquals(feature.getName(), "featureX");
        assertEquals(feature.getProject(), "default");
        assertThat(feature.getType()).isEmpty();
    }

    @Test
    public void test_project_is_null_if_not_in_original() {
        String basicFeatures =
                "{\"features\":[{\"name\":\"featureX\",\"enabled\":true,\"strategies\":[{\"name\":\"default\"}]}]}";
        List<FeatureDefinition> parsed = ClientFeaturesParser.parse(basicFeatures);

        assertEquals(1, parsed.size());

        FeatureDefinition feature = parsed.get(0);

        assertEquals(feature.getName(), "featureX");
        assertEquals(feature.getProject(), null);
    }

    @Test
    public void test_type_is_set_if_present() {
        String basicFeatures =
                "{\"features\":[{\"name\":\"featureX\",\"type\":\"experiment\",\"enabled\":true,\"strategies\":[{\"name\":\"default\"}]}]}";
        List<FeatureDefinition> parsed = ClientFeaturesParser.parse(basicFeatures);

        assertEquals(1, parsed.size());

        FeatureDefinition feature = parsed.get(0);

        assertEquals(feature.getName(), "featureX");
        assertThat(feature.getType()).isNotEmpty();
        assertEquals(feature.getType().get(), "experiment");
    }

    @Test
    public void test_deserialize_fails_if_name_is_not_set() {
        String basicFeatures =
                "{\"features\":[{\"project\":\"default\",\"enabled\":true,\"strategies\":[{\"name\":\"default\"}]}]}";

        try {
            ClientFeaturesParser.parse(basicFeatures);
            assertThat(false).isTrue();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(RuntimeException.class);
            assertThat(e.getMessage()).contains("Missing required field 'name'");
        }
    }

    @Test
    public void test_enabled_property_returned_if_set() {
        String basicFeatures =
                "{\"features\":[{\"name\":\"featureX\",\"project\":\"default\",\"enabled\":true,\"strategies\":[{\"name\":\"default\"}]}]}";
        List<FeatureDefinition> parsed = ClientFeaturesParser.parse(basicFeatures);

        assertEquals(1, parsed.size());

        FeatureDefinition feature = parsed.get(0);

        assertEquals(feature.getName(), "featureX");
        assertEquals(feature.getProject(), "default");
        assertEquals(feature.environmentEnabled(), true);
    }

    @Test
    public void test_enabled_property_defaults_to_false() {
        String basicFeatures =
                "{\"features\":[{\"name\":\"featureX\",\"project\":\"default\",\"strategies\":[{\"name\":\"default\"}]}]}";
        List<FeatureDefinition> parsed = ClientFeaturesParser.parse(basicFeatures);

        assertEquals(1, parsed.size());

        FeatureDefinition feature = parsed.get(0);

        assertEquals(feature.getName(), "featureX");
        assertEquals(feature.getProject(), "default");
        assertEquals(feature.environmentEnabled(), false);
    }
}
