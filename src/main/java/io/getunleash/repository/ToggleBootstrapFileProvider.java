package io.getunleash.repository;

import io.getunleash.lang.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToggleBootstrapFileProvider implements ToggleBootstrapProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ToggleBootstrapFileProvider.class);
    private static final String CLASSPATH_PREFIX = "classpath:";
    final String path;

    public ToggleBootstrapFileProvider() {
        this.path = getBootstrapFile();
    }

    /**
     * Accepts path to file to read either as constructor parameter or as environment variable in
     * "UNLEASH_BOOTSTRAP_FILE"
     *
     * @param path - path to toggles file
     */
    public ToggleBootstrapFileProvider(String path) {
        this.path = path;
    }

    @Override
    public Optional<String> read() {
        LOG.info("Trying to read feature toggles from bootstrap file found at {}", path);
        if (path == null) {
            return Optional.empty();
        }
        try {
            if (path.startsWith(CLASSPATH_PREFIX)) {
                return readFromClasspath(
                        getClass().getClassLoader(), path.substring(CLASSPATH_PREFIX.length()));
            }
            return Optional.of(
                    new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
        } catch (IOException ioEx) {
            LOG.warn("Could not read bootstrap file at {}", path, ioEx);
            return Optional.empty();
        }
    }

    /**
     * Reads a classpath resource as a stream instead of resolving it to a {@link
     * java.nio.file.Path}. A classpath resource is only a real file when the classpath entry is an
     * exploded directory - packaged in a jar its URL has the "jar:" scheme, which {@code
     * Paths.get(URI)} rejects with {@code FileSystemNotFoundException}.
     *
     * <p>Visible for testing so the jar case can be exercised with a throwaway class loader.
     */
    static Optional<String> readFromClasspath(ClassLoader classLoader, String resource)
            throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(normalise(resource))) {
            if (in == null) {
                LOG.warn("Could not find {} on the classpath", resource);
                return Optional.empty();
            }
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * {@link ClassLoader#getResourceAsStream(String)} wants a name with no leading separator, but
     * classpath references are commonly written with one.
     */
    private static String normalise(String resource) {
        if (resource.startsWith("./")) {
            return resource.substring(2);
        }
        if (resource.startsWith("/")) {
            return resource.substring(1);
        }
        return resource;
    }

    @Nullable
    private String getBootstrapFile() {
        String path = System.getenv("UNLEASH_BOOTSTRAP_FILE");
        if (path == null) {
            path = System.getProperty("UNLEASH_BOOTSTRAP_FILE");
        }
        return path;
    }
}
