package com.familyagent.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
public class ExternalClientConfig {

    private static final int MAX_CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int MINIO_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int MINIO_WRITE_TIMEOUT_SECONDS = 120;
    private static final int MINIO_READ_TIMEOUT_SECONDS = 120;

    @Bean
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                   @Value("${minio.access-key}") String accessKey,
                                   @Value("${minio.secret-key}") String secretKey) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(MINIO_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(MINIO_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(MINIO_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(normalizeEndpoint(endpoint))
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }

    @Bean
    public RestTemplate aiServiceRestTemplate(RestTemplateBuilder builder,
                                              @Value("${ai-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                              @Value("${ai-service.read-timeout-seconds:${ai-service.timeout:120}}") int readTimeoutSeconds) {
        return aiRestTemplate(builder, connectTimeoutSeconds, readTimeoutSeconds);
    }

    @Bean
    public RestTemplate aiServiceStreamRestTemplate(RestTemplateBuilder builder,
                                                    @Value("${ai-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                                    @Value("${ai-service.stream-read-timeout-seconds:45}") int readTimeoutSeconds) {
        return aiRestTemplate(builder, connectTimeoutSeconds, readTimeoutSeconds);
    }

    private RestTemplate aiRestTemplate(RestTemplateBuilder builder, int connectTimeoutSeconds, int readTimeoutSeconds) {
        int connectTimeoutMillis = Math.max(1, connectTimeoutSeconds) * 1000;
        int readTimeoutMillis = Math.max(1, readTimeoutSeconds) * 1000;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.min(connectTimeoutMillis, MAX_CONNECT_TIMEOUT_MILLIS));
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
