package io.getunleash.event;

import io.getunleash.UnleashException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GatedEventEmitter {
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final EventDispatcher eventDispatcher;

    public GatedEventEmitter(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    public void ready() {
        if (ready.compareAndSet(false, true)) {
            eventDispatcher.dispatch(new UnleashReady());
        }
    }

    public void update(ClientFeaturesResponse clientFeaturesResponse) {
        eventDispatcher.dispatch(clientFeaturesResponse);
    }

    public void error(UnleashException exception) {
        eventDispatcher.dispatch(exception);
    }
}
