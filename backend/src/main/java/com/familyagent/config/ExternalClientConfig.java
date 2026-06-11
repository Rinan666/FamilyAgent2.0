package com.familyagent.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Spring-managed external client bean configuration.
 * <p>
 * Replaces manual {@code new MinioClient(...)} and {@code new RestTemplate(...)}
 * so that Spring manages the full lifecycle (creation, configuration, shutdown).
 */
@Configuration
public class ExternalClientConfig {

    private static final int MAX_CONNECT_TIMEOUT_MILLIS = 10_000;

    @Bean
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                   @Value("${minio.access-key}") String accessKey,
                                   @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(normalizeEndpoint(endpoint))
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public RestTemplate aiServiceRestTemplate(RestTemplateBuilder builder,
                                              @Value("${ai-service.timeout:60}") int timeoutSeconds) {
        int readTimeoutMillis = Math.max(1, timeoutSeconds) * 1000;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.min(readTimeoutMillis, MAX_CONNECT_TIMEOUT_MILLIS));
        requestFactory.setReadTimeout(readTimeoutMillis);
        return builder
                .requestFactory(() -> requestFactory)
                .build();
    }

    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("MinIO endpoint must not be blank");
        }
        String normalized = endpoint.trim();
        if (!normalized.contains("://")) {
            normalized = "http://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
