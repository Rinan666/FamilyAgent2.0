package com.familyagent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads repo-root .env values as low-priority defaults for local development.
 */
public final class LocalEnvDefaults {

    private static final String ENV_FILE_OVERRIDE = "FAMILYAGENT_ENV_FILE";
    private static final String DEFAULT_ENV_FILE = "../.env";

    private LocalEnvDefaults() {
    }

    public static Map<String, Object> load() {
        Path envFile = resolveEnvFile();
        if (!Files.isRegularFile(envFile)) {
            return Map.of();
        }

        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                values.putIfAbsent(key, value);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read local env file: " + envFile, ex);
        }

        return values;
    }

    private static Path resolveEnvFile() {
        String override = System.getenv(ENV_FILE_OVERRIDE);
        String candidate = override == null || override.isBlank() ? DEFAULT_ENV_FILE : override.trim();
        Path path = Paths.get(candidate);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get("").toAbsolutePath().resolve(path).normalize();
    }
}
