package io.getunleash.util;

import static io.getunleash.util.UnleashConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.getunleash.CustomHttpHeadersProvider;
import io.getunleash.DefaultCustomHttpHeadersProviderImpl;
import io.getunleash.RunOnJavaVersions;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.net.Authenticator.RequestorType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

public class UnleashConfigTest {

    @Test
    public void should_require_unleasAPI_url() {
        Executable ex = () -> UnleashConfig.builder().appName("test").build();
        assertThrows(IllegalStateException.class, ex);
    }

    @Test
    public void should_require_app_name() {
        Executable ex = () -> UnleashConfig.builder().unleashAPI("http://unleash.com").build();
        assertThrows(IllegalStateException.class, ex);
    }

    @Test
    public void should_require_valid_uri() {
        Executable ex = () -> UnleashConfig.builder().unleashAPI("this is not a uri").build();
        assertThrows(IllegalArgumentException.class, ex);
    }

    @Test
    public void should_build_config() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("my-app")
                        .instanceId("my-instance-1")
                        .unleashAPI("http://unleash.org")
                        .build();

        assertThat(config.getAppName()).isEqualTo("my-app");
        assertThat(config.getInstanceId()).isEqualTo("my-instance-1");
        assertThat(config.getUnleashAPI()).isEqualTo(URI.create("http://unleash.org"));
    }

    @Test
    public void should_generate_backupfile() {
        UnleashConfig config =
                UnleashConfig.builder().appName("my-app").unleashAPI("http://unleash.org").build();

        assertThat(config.getAppName()).isEqualTo("my-app");
        String tmpDir = System.getProperty("java.io.tmpdir");
        tmpDir = tmpDir.endsWith(File.separator) ? tmpDir : tmpDir + File.separatorChar;
        assertThat(config.getBackupFile()).isEqualTo(tmpDir + "unleash-my-app-repo.json");
    }

    @Test
    public void should_generate_backupfile_app_name_with_slash() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("fix/baz-123456")
                        .unleashAPI("http://unleash.org")
                        .build();

        assertThat(config.getAppName()).isEqualTo("fix/baz-123456");
        String tmpDir = System.getProperty("java.io.tmpdir");
        tmpDir = tmpDir.endsWith(File.separator) ? tmpDir : tmpDir + File.separatorChar;
        assertThat(config.getBackupFile())
                .isEqualTo(tmpDir + "unleash-" + "fix-baz-123456" + "-repo.json");
    }

    @Test
    public void should_generate_backupfile_app_name_with_backslash() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("fix\\baz-123456")
                        .unleashAPI("http://unleash.org")
                        .build();

        assertThat(config.getAppName()).isEqualTo("fix\\baz-123456");
        String tmpDir = System.getProperty("java.io.tmpdir");
        tmpDir = tmpDir.endsWith(File.separator) ? tmpDir : tmpDir + File.separatorChar;
        assertThat(config.getBackupFile())
                .isEqualTo(tmpDir + "unleash-" + "fix-baz-123456" + "-repo.json");
    }

    @Test
    public void should_use_provided_backupfile() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("my-app")
                        .backupFile("/test/unleash-backup.json")
                        .unleashAPI("http://unleash.org")
                        .build();

        assertThat(config.getAppName()).isEqualTo("my-app");
        assertThat(config.getBackupFile()).isEqualTo("/test/unleash-backup.json");
    }

    @Test
    public void should_set_sdk_version() {
        UnleashConfig config =
                UnleashConfig.builder().appName("my-app").unleashAPI("http://unleash.org").build();

        assertThat(config.getSdkVersion()).isEqualTo("unleash-java-sdk:development");
    }

    @Test
    public void should_set_environment_to_default() {
        UnleashConfig config =
                UnleashConfig.builder().appName("my-app").unleashAPI("http://unleash.org").build();

        assertThat(config.getEnvironment()).isEqualTo("default");
    }

    @Test
    public void should_set_environment() {
        String env = "prod";
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("my-app")
                        .environment(env)
                        .unleashAPI("http://unleash.org")
                        .build();

        assertThat(config.getEnvironment()).isEqualTo(env);
    }

    @Test
    public void should_add_client_identification_headers_to_connection() throws IOException {
        String appName = "my-app";
        String instanceId = "my-instance-1";
        String unleashAPI = "http://unleash.org";

        UnleashConfig unleashConfig =
                UnleashConfig.builder()
                        .appName(appName)
                        .instanceId(instanceId)
                        .unleashAPI(unleashAPI)
                        .build();

        URL someUrl = new URL(unleashAPI + "/some/arbitrary/path");
        HttpURLConnection connection = (HttpURLConnection) someUrl.openConnection();

        UnleashConfig.setRequestProperties(connection, unleashConfig);
        assertThat(connection.getRequestProperty(UNLEASH_APP_NAME_HEADER)).isEqualTo(appName);
        assertThat(connection.getRequestProperty(UNLEASH_INSTANCE_ID_HEADER)).isEqualTo(instanceId);
        assertThat(connection.getRequestProperty(UNLEASH_CONNECTION_ID_HEADER))
                .isEqualTo(unleashConfig.getConnectionId());
        assertThat(connection.getRequestProperty(UNLEASH_SDK_HEADER))
                .isEqualTo("unleash-java-sdk:development");
        assertThat(connection.getRequestProperty("User-Agent")).isEqualTo(appName);
    }

    @Test
    public void should_add_custom_headers_to_connection_if_present() throws IOException {
        String unleashAPI = "http://unleash.org";
        String headerName = "UNLEASH-CUSTOM-TEST-HEADER";
        String headerValue = "Some value";

        UnleashConfig unleashConfig =
                UnleashConfig.builder()
                        .appName("my-app")
                        .instanceId("my-instance-1")
                        .unleashAPI(unleashAPI)
                        .customHttpHeader(headerName, headerValue)
                        .build();

        URL someUrl = new URL(unleashAPI + "/some/arbitrary/path");
        HttpURLConnection connection = (HttpURLConnection) someUrl.openConnection();

        UnleashConfig.setRequestProperties(connection, unleashConfig);
        assertThat(connection.getRequestProperty(headerName)).isEqualTo(headerValue);
    }

    @Test
    public void should_add_custom_headers_from_provider_to_connection_if_present()
            throws IOException {
        String unleashAPI = "http://unleash.org";
        Map<String, String> result =
                new HashMap() {
                    {
                        put("PROVIDER-HEADER", "Provider Value");
                    }
                };

        CustomHttpHeadersProvider provider =
                Mockito.mock(DefaultCustomHttpHeadersProviderImpl.class);
        when(provider.getCustomHeaders()).thenReturn(result);

        UnleashConfig unleashConfig =
                UnleashConfig.builder()
                        .appName("my-app")
                        .instanceId("my-instance-1")
                        .unleashAPI(unleashAPI)
                        .customHttpHeadersProvider(provider)
                        .build();

        URL someUrl = new URL(unleashAPI + "/some/arbitrary/path");
        HttpURLConnection connection = (HttpURLConnection) someUrl.openConnection();

        UnleashConfig.setRequestProperties(connection, unleashConfig);

        for (String key : result.keySet()) {
            assertThat(connection.getRequestProperty(key)).isEqualTo(result.get(key));
        }
    }

    @Test
    public void should_require_instanceId() {
        Executable ex =
                () ->
                        UnleashConfig.builder()
                                .appName("my-app")
                                .instanceId(null)
                                .unleashAPI("http://unleash.org")
                                .build();

        assertThrows(IllegalStateException.class, ex);
    }

    @Test
    @RunOnJavaVersions(javaVersions = {"1.8", "11"})
    public void should_enable_proxy_based_on_jvm_settings()
            throws IllegalAccessException, NoSuchFieldException {

        String proxyHost = "proxy-host";
        String proxyPort = "8080";
        String proxyUser = "my-proxy-user";
        String proxyPassword = "my-secret";

        System.setProperty("http.proxyHost", proxyHost);
        System.setProperty("http.proxyPort", proxyPort);
        System.setProperty("http.proxyUser", proxyUser);
        System.setProperty("http.proxyPassword", proxyPassword);

        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("my-app")
                        .unleashAPI("http://unleash.org")
                        .enableProxyAuthenticationByJvmProperties()
                        .build();

        Field authenticator = Authenticator.class.getDeclaredField("theAuthenticator");
        authenticator.setAccessible(true);
        UnleashConfig.SystemProxyAuthenticator proxyAuthenticator =
                (UnleashConfig.SystemProxyAuthenticator) authenticator.get(null);

        Field requestingAuthType =
                proxyAuthenticator
                        .getClass()
                        .getSuperclass()
                        .getDeclaredField("requestingAuthType");
        requestingAuthType.setAccessible(true);
        requestingAuthType.set(proxyAuthenticator, RequestorType.PROXY);

        Field requestingProtocol =
                proxyAuthenticator
                        .getClass()
                        .getSuperclass()
                        .getDeclaredField("requestingProtocol");
        requestingProtocol.setAccessible(true);
        requestingProtocol.set(proxyAuthenticator, "http");

        Field requestingHost =
                proxyAuthenticator.getClass().getSuperclass().getDeclaredField("requestingHost");
        requestingHost.setAccessible(true);
        requestingHost.set(proxyAuthenticator, proxyHost);

        Field requestingPort =
                proxyAuthenticator.getClass().getSuperclass().getDeclaredField("requestingPort");
        requestingPort.setAccessible(true);
        requestingPort.set(proxyAuthenticator, 8080);

        PasswordAuthentication passwordAuthentication =
                proxyAuthenticator.getPasswordAuthentication();

        assertThat(passwordAuthentication.getUserName()).isEqualTo(proxyUser);
        assertThat(new String(passwordAuthentication.getPassword())).isEqualTo(proxyPassword);
        assertThat(config.isProxyAuthenticationByJvmProperties()).isEqualTo(true);
    }

    @Test
    @RunOnJavaVersions(javaVersions = {"1.8", "11"})
    public void should_use_specified_proxy() throws IllegalAccessException, NoSuchFieldException {

        String proxyHost = "proxy-host";
        int proxyPort = 8080;
        String proxyUser = "my-proxy-user";
        String proxyPassword = "my-secret";

        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("my-app")
                        .unleashAPI("http://unleash.org")
                        .proxy(
                                new Proxy(
                                        Proxy.Type.HTTP,
                                        new InetSocketAddress(proxyHost, proxyPort)),
                                proxyUser,
                                proxyPassword)
                        .build();
        Field authenticator = Authenticator.class.getDeclaredField("theAuthenticator");
        authenticator.setAccessible(true);
        UnleashConfig.CustomProxyAuthenticator proxyAuthenticator =
                (UnleashConfig.CustomProxyAuthenticator) authenticator.get(null);

        Field requestingAuthType =
                proxyAuthenticator
                        .getClass()
                        .getSuperclass()
                        .getDeclaredField("requestingAuthType");
        requestingAuthType.setAccessible(true);
        requestingAuthType.set(proxyAuthenticator, RequestorType.PROXY);

        Field requestingProtocol =
                proxyAuthenticator
                        .getClass()
                        .getSuperclass()
                        .getDeclaredField("requestingProtocol");
        requestingProtocol.setAccessible(true);
        requestingProtocol.set(proxyAuthenticator, "http");

        Field requestingHost =
                proxyAuthenticator.getClass().getSuperclass().getDeclaredField("requestingHost");
        requestingHost.setAccessible(true);
        requestingHost.set(proxyAuthenticator, proxyHost);

        Field requestingPort =
                proxyAuthenticator.getClass().getSuperclass().getDeclaredField("requestingPort");
        requestingPort.setAccessible(true);
        requestingPort.set(proxyAuthenticator, 8080);

        PasswordAuthentication passwordAuthentication =
                proxyAuthenticator.getPasswordAuthentication();

        assertThat(passwordAuthentication.getUserName()).isEqualTo(proxyUser);
        assertThat(new String(passwordAuthentication.getPassword())).isEqualTo(proxyPassword);
        assertThat(config.isProxyAuthenticationByJvmProperties()).isEqualTo(false);
    }

    @Test
    public void clientIdentifierIsDeterministic() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .apiKey("someapikey")
                        .appName("test-app")
                        .instanceId("myinstance")
                        .unleashAPI("http://localhost:4242")
                        .build();
        assertThat(config.getClientIdentifier()).isEqualTo(config.getClientIdentifier());
    }

    @Test
    public void clientIdentifierWithoutApiKeyShouldNotThrowException() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("test-app")
                        .instanceId("myiunstance")
                        .unleashAPI("http://localhost:4242")
                        .build();
        assertDoesNotThrow(config::getClientIdentifier);
    }

    @Test
    public void clientIdentifierWithoutInstanceIdStillWorks() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .apiKey("someapikey")
                        .appName("test-app")
                        .unleashAPI("http://localhost:4242")
                        .build();
        assertDoesNotThrow(config::getClientIdentifier);
    }

    @Test
    public void should_require_unleash_uri() {
        Executable ex =
                () -> UnleashConfig.builder().apiKey("someapikey").appName("my-app").build();

        assertThrows(IllegalStateException.class, ex);
    }
}
