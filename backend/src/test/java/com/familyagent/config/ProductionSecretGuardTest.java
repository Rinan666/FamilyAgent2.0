package com.familyagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecretGuardTest {

    @Test
    void run_shouldAllowDefaultsInDevelopment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENV", "dev")
                .withProperty("DB_PASSWORD", "fa_dev_pass")
                .withProperty("REDIS_PASSWORD", "ASDFGZXCVB008");
        ProductionSecretGuard guard = new ProductionSecretGuard(environment);

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments(new String[]{})));
    }

    @Test
    void run_shouldRejectDefaultSecretsInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENV", "prod")
                .withProperty("DB_PASSWORD", "fa_dev_pass")
                .withProperty("REDIS_PASSWORD", "ASDFGZXCVB008")
                .withProperty("RABBITMQ_PASSWORD", "secure-rabbit")
                .withProperty("AI_INTERNAL_SERVICE_TOKEN", "secure-token")
                .withProperty("MINIO_ACCESS_KEY", "secure-minio-user")
                .withProperty("MINIO_SECRET_KEY", "secure-minio-secret");
        ProductionSecretGuard guard = new ProductionSecretGuard(environment);

        assertThrows(IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments(new String[]{})));
    }

    @Test
    void run_shouldAllowNonDefaultSecretsInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENV", "prod")
                .withProperty("DB_PASSWORD", "secure-db")
                .withProperty("REDIS_PASSWORD", "secure-redis")
                .withProperty("RABBITMQ_PASSWORD", "secure-rabbit")
                .withProperty("AI_INTERNAL_SERVICE_TOKEN", "secure-token")
                .withProperty("MINIO_ACCESS_KEY", "secure-minio-user")
                .withProperty("MINIO_SECRET_KEY", "secure-minio-secret");
        ProductionSecretGuard guard = new ProductionSecretGuard(environment);

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments(new String[]{})));
    }
}
