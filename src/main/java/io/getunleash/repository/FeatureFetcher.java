package io.getunleash.repository;

import io.getunleash.UnleashException;
import io.getunleash.event.ClientFeaturesResponse;

public interface FeatureFetcher {
    ClientFeaturesResponse fetchFeatures() throws UnleashException;
}
