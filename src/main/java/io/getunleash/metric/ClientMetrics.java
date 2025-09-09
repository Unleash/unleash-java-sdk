package io.getunleash.metric;

import io.getunleash.engine.MetricsBucket;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.event.UnleashEvent;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.lang.Nullable;
import io.getunleash.util.UnleashConfig;

public class ClientMetrics implements UnleashEvent {

    private final String appName;
    private final String instanceId;
    private final String connectionId;
    @Nullable private final MetricsBucket bucket;
    private final String environment;
    private final String specVersion;
    @Nullable private final String platformName;
    @Nullable private final String platformVersion;
    @Nullable private final String yggdrasilVersion;

    ClientMetrics(UnleashConfig config, @Nullable MetricsBucket bucket) {
        this.environment = config.getEnvironment();
        this.appName = config.getAppName();
        this.instanceId = config.getInstanceId();
        this.connectionId = config.getConnectionId();
        this.bucket = bucket;
        this.specVersion = config.getClientSpecificationVersion();
        this.platformName = System.getProperty("java.vm.name");
        this.platformVersion = System.getProperty("java.version");
        this.yggdrasilVersion = UnleashEngine.getCoreVersion();
    }

    public String getAppName() {
        return appName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    @Nullable
    public MetricsBucket getBucket() {
        return bucket;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    @Nullable
    public String getPlatformName() {
        return platformName;
    }

    @Nullable
    public String getPlatformVersion() {
        return platformVersion;
    }

    @Nullable
    public String getYggdrasilVersion() {
        return yggdrasilVersion;
    }

    @Override
    public void publishTo(UnleashSubscriber unleashSubscriber) {
        unleashSubscriber.clientMetrics(this);
    }

    @Override
    public String toString() {
        return "metrics:"
                + " appName="
                + appName
                + " instanceId="
                + instanceId
                + " connectionId="
                + connectionId;
    }
}
