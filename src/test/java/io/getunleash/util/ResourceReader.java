package io.getunleash.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ResourceReader {
    public static String readResourceAsString(String resourceName) {
        var resourceUrl = ResourceReader.class.getClassLoader().getResource(resourceName);
        if (resourceUrl == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        try {
            return Files.readString(Paths.get(resourceUrl.getPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
