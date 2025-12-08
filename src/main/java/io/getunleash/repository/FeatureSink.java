package io.getunleash.repository;

import io.getunleash.event.ClientFeaturesResponse;

interface FeatureSink {
    void accept(ClientFeaturesResponse clientFeatures);
}
