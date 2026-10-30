package io.getunleash.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToggleBootstrapFileProviderTest {

    @Test
    public void shouldBeAbleToLoadFilePassedInAsArgument() {
        File exampleRepoFile =
                new File(getClass().getClassLoader().getResource("unleash-repo-v0.json").getFile());
        ToggleBootstrapFileProvider toggleBootstrapFileProvider =
                new ToggleBootstrapFileProvider(exampleRepoFile.getAbsolutePath());
        assertThat(toggleBootstrapFileProvider.read()).isNotEmpty();
    }

    @Test
    public void shouldBeAbleToLoadFilePassedInEnvironment() {
        File exampleRepoFile =
                new File(getClass().getClassLoader().getResource("unleash-repo-v0.json").getFile());
        System.setProperty("UNLEASH_BOOTSTRAP_FILE", exampleRepoFile.getAbsolutePath());
        ToggleBootstrapFileProvider toggleBootstrapFileProvider = new ToggleBootstrapFileProvider();
        assertThat(toggleBootstrapFileProvider.read()).isNotEmpty();
    }

    @Test
    public void shouldBeAbleToLoadfileFromClasspathReference() {
        System.setProperty("UNLEASH_BOOTSTRAP_FILE", "classpath:unleash-repo-v0.json");
        ToggleBootstrapFileProvider bootstrap = new ToggleBootstrapFileProvider();

        Optional<String> read = bootstrap.read();
        assertThat(read).isPresent();
    }

    @Test
    public void shouldAcceptClasspathReferenceWithLeadingSlash() {
        assertThat(new ToggleBootstrapFileProvider("classpath:/unleash-repo-v0.json").read())
                .isPresent();
    }

    @Test
    public void shouldAcceptClasspathReferenceRelativeToTheRoot() {
        assertThat(new ToggleBootstrapFileProvider("classpath:./unleash-repo-v0.json").read())
                .isPresent();
    }

    @Test
    public void shouldReadClasspathResourcePackagedInAJar(@TempDir Path tempDir) throws Exception {
        String json = "{\"version\": 2, \"features\": []}";
        URL jar = jarContaining(tempDir, "bootstrap-toggles.json", json);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jar}, null)) {
            assertThat(
                            ToggleBootstrapFileProvider.readFromClasspath(
                                    classLoader, "bootstrap-toggles.json"))
                    .contains(json);
            assertThat(
                            ToggleBootstrapFileProvider.readFromClasspath(
                                    classLoader, "/bootstrap-toggles.json"))
                    .contains(json);
        }
    }

    @Test
    public void shouldReturnEmptyWhenClasspathResourceIsMissing() {
        assertThat(new ToggleBootstrapFileProvider("classpath:no-such-toggles.json").read())
                .isEmpty();
    }

    @Test
    public void shouldReturnEmptyWhenFileIsMissing(@TempDir Path tempDir) {
        String missing = tempDir.resolve("no-such-toggles.json").toString();
        assertThat(new ToggleBootstrapFileProvider(missing).read()).isEmpty();
    }

    private static URL jarContaining(Path dir, String entryName, String content)
            throws IOException {
        Path jar = dir.resolve("bootstrap.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(entryName));
            write(out, content);
            out.closeEntry();
        }
        return jar.toUri().toURL();
    }

    private static void write(OutputStream out, String content) throws IOException {
        out.write(content.getBytes(StandardCharsets.UTF_8));
    }
}
