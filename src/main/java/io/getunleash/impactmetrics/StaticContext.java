package io.getunleash.impactmetrics;

public class StaticContext {
    private final String appName;
    private final String environment;

    public StaticContext(String appName, String environment) {
        this.appName = appName;
        this.environment = environment;
    }

    public String getAppName() {
        return appName;
    }

    public String getEnvironment() {
        return environment;
    }
}
