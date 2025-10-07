package io.getunleash.unleash.example;

import io.getunleash.Unleash;
import jakarta.websocket.server.PathParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class ProxyController {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyController.class);
    private Unleash unleash;
    @Autowired
    public ProxyController(Unleash unleash) {
        this.unleash = unleash;
    }


    @GetMapping("/")
    public Map<String, Boolean> getEnabledToggles() {
        LOG.info("Getting enabled toggles.");
        return toggles()
            .filter(Map.Entry::getValue)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @GetMapping("/all")
    public Map<String, Boolean> getAllToggles() {
        return toggles().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @GetMapping("/toggle/{toggleName}")
    public Boolean getToggle(@PathParam("toggleName") String name) {

        LOG.info("Fetching toggles");
        return unleash.isEnabled(name);
    }

    private Stream<Map.Entry<String, Boolean>> toggles() {
        return unleash.more().getFeatureToggleNames().stream().map(name -> Map.entry(name, unleash.isEnabled(name)));
    }
}
