package io.getunleash;

import static org.assertj.core.api.Assertions.assertThat;

import io.getunleash.util.UnleashConfig;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.*;

class HotReloadSchedulerReuseTest {

    private UnleashConfig baseConfig() {
        return UnleashConfig.builder()
                .appName("hot-reload-test-app")
                .instanceId("A")
                .unleashAPI("http://localhost") // never hit
                .synchronousFetchOnInitialisation(false)
                .fetchTogglesInterval(100)
                .sendMetricsInterval(100)
                .build();
    }

    private ScheduledThreadPoolExecutor currentGlobalExecutor() throws Exception {
        Class<?> execClazz = Class.forName("io.getunleash.util.UnleashScheduledExecutorImpl");
        Field instance = execClazz.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object inst = instance.get(null);
        if (inst == null) return null;

        Field pool = execClazz.getDeclaredField("scheduledThreadPoolExecutor");
        pool.setAccessible(true);
        return (ScheduledThreadPoolExecutor) pool.get(inst);
    }

    @Test
    void secondClientDoesNotReuseSchedulerExecutor() throws Exception {
        // 1) Create first client; let it schedule background tasks
        DefaultUnleash first = new DefaultUnleash(baseConfig());

        // Let it initialize/schedule
        Thread.sleep(150);

        // Snapshot the global executor
        ScheduledThreadPoolExecutor execBeforeShutdown = currentGlobalExecutor();
        assertThat(execBeforeShutdown).isNotNull();
        assertThat(execBeforeShutdown.isShutdown()).isFalse();

        // 2) Simulate app stop (DevTools restart) by shutting the client
        first.shutdown();

        // After shutdown, the global executor instance is properly reset to null
        ScheduledThreadPoolExecutor execAfterShutdown = currentGlobalExecutor();
        assertThat(execAfterShutdown).isNull();

        // 3) "Reloaded app": create a second client in the same JVM (statics still around)
        DefaultUnleash second = new DefaultUnleash(baseConfig());

        // 4) Assert that the second client creates a fresh executor (not reusing the terminated
        // one)
        ScheduledThreadPoolExecutor execUsedBySecond = currentGlobalExecutor();
        assertThat(execUsedBySecond).isNotNull();
        assertThat(execUsedBySecond.isShutdown()).isFalse(); // it's a new one

        // Clean up
        second.shutdown();
    }
}
