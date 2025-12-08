package io.getunleash.repository;

import io.getunleash.UnleashException;
import io.getunleash.event.ClientFeaturesResponse;

public interface FeatureSource {
    ClientFeaturesResponse fetchFeatures() throws UnleashException;
}
