package com.familyagent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fails fast when production-like deployments still rely on local/demo secrets.
 */
@Component
@RequiredArgsConstructor
public class ProductionSecretGuard implements ApplicationRunner {

    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "development", "local", "test");
    private static final Map<String, Set<String>> FORBIDDEN_VALUES = new LinkedHashMap<>();

    static {
        FORBIDDEN_VALUES.put("DB_PASSWORD", Set.of("", "fa_dev_pass"));
        FORBIDDEN_VALUES.put("REDIS_PASSWORD", Set.of("", "ASDFGZXCVB008"));
        FORBIDDEN_VALUES.put("RABBITMQ_PASSWORD", Set.of("", "fa_dev_pass"));
        FORBIDDEN_VALUES.put("AI_INTERNAL_SERVICE_TOKEN", Set.of("", "familyagent-dev-internal-token"));
        FORBIDDEN_VALUES.put("MINIO_ACCESS_KEY", Set.of("", "minioadmin"));
        FORBIDDEN_VALUES.put("MINIO_SECRET_KEY", Set.of("", "minioadmin"));
    }

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isProductionLike()) {
            return;
        }

        StringBuilder failures = new StringBuilder();
        FORBIDDEN_VALUES.forEach((key, forbiddenValues) -> {
            String value = normalize(environment.getProperty(key));
            if (forbiddenValues.contains(value)) {
                if (!failures.isEmpty()) {
                    failures.append(", ");
                }
                failures.append(key);
            }
        });

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start production-like backend with missing or default secrets: " + failures);
        }
    }

    private boolean isProductionLike() {
        String appEnv = normalize(environment.getProperty("APP_ENV"));
        if (!appEnv.isEmpty()) {
            return !DEVELOPMENT_PROFILES.contains(appEnv.toLowerCase(Locale.ROOT));
        }
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .noneMatch(DEVELOPMENT_PROFILES::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
