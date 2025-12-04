package io.getunleash.util;

import io.getunleash.lang.Nullable;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnleashScheduledExecutorImpl implements UnleashScheduledExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(UnleashScheduledExecutorImpl.class);

    @Nullable private static UnleashScheduledExecutorImpl INSTANCE;

    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private final ExecutorService executorService;

    public UnleashScheduledExecutorImpl() {
        ThreadFactory threadFactory =
                runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("unleash-api-executor");
                    thread.setDaemon(true);
                    return thread;
                };

        this.scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, threadFactory);
        this.scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);

        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
    }

    public static synchronized UnleashScheduledExecutorImpl getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new UnleashScheduledExecutorImpl();
        }
        return INSTANCE;
    }

    @Override
    public void setInterval(Runnable command, long initialDelaySec, long periodSec) {
        try {
            scheduledThreadPoolExecutor.scheduleAtFixedRate(
                    command, initialDelaySec, periodSec, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ex) {
            LOG.error("Unleash background task crashed", ex);
        }
    }

    @Override
    public Future<Void> scheduleOnce(Runnable runnable) {
        return executorService.submit(
                () -> {
                    runnable.run();
                    return null;
                });
    }

    @Override
    public synchronized void shutdown() {
        this.scheduledThreadPoolExecutor.shutdown();
        this.executorService.shutdown();
        INSTANCE = null;
    }

    @Override
    public synchronized void shutdownNow() {
        this.scheduledThreadPoolExecutor.shutdownNow();
        this.executorService.shutdownNow();
        INSTANCE = null;
    }
}
