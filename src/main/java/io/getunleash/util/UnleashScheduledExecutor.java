package io.getunleash.util;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public interface UnleashScheduledExecutor {

    void setInterval(Runnable command, long initialDelaySec, long periodSec)
            throws RejectedExecutionException;

    Future<Void> scheduleOnce(Runnable runnable);

    default void shutdown() {}

    default void shutdownNow() {}
}
