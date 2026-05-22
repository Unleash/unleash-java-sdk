package io.getunleash.repository;

import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.util.UnleashConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class GraalVmHttpFeatureFetcherSmoke {
    private static final String FEATURE_NAME = "graalvm-smoke-test";
    private static final String RESPONSE_BODY =
            "{\"version\":1,\"features\":[{\"name\":\""
                    + FEATURE_NAME
                    + "\",\"enabled\":true,\"strategies\":[{\"name\":\"default\"}]}]}";

    public static void main(String[] args) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread server = new Thread(() -> serveOneRequest(serverSocket));
            server.start();

            UnleashConfig config =
                    UnleashConfig.builder()
                            .appName("graalvm-native-image-smoke-test")
                            .unleashAPI(
                                    new URI(
                                            "http://127.0.0.1:"
                                                    + serverSocket.getLocalPort()
                                                    + "/api/"))
                            .build();

            ClientFeaturesResponse response = new HttpFeatureFetcher(config).fetchFeatures();
            boolean found =
                    response.getFeatures().stream()
                            .anyMatch(feature -> FEATURE_NAME.equals(feature.getName()));
            if (response.getStatus() != ClientFeaturesResponse.Status.CHANGED || !found) {
                throw new IllegalStateException("Native image smoke test did not fetch features");
            }

            server.join();
        }
    }

    private static void serveOneRequest(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream(), StandardCharsets.US_ASCII));
                OutputStream output = socket.getOutputStream()) {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}

            byte[] body = RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
            output.write(
                    ("HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: application/json\r\n"
                                    + "Content-Length: "
                                    + body.length
                                    + "\r\n"
                                    + "Connection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
