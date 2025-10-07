package io.getunleash.unleash.example;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UnleashSpringConfig {

    @Bean
    public UnleashConfig unleashConfig(@Value("${unleash.url}") String url, @Value("${unleash.apikey}") String apiKey,
            @Value("${unleash.appname}") String appName) {
        UnleashConfig config = UnleashConfig.builder().unleashAPI(url).apiKey(apiKey).appName(appName)
                .synchronousFetchOnInitialisation(false)
                .fetchTogglesInterval(15).build();
        return config;
    }

    @Bean
    public Unleash unleash(UnleashConfig unleashConfig) {
        return new DefaultUnleash(unleashConfig);
    }
}
