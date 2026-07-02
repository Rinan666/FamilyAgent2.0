package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIServiceClientResilienceTest {

    private final RestTemplate aiRestTemplate = mock(RestTemplate.class);
    private final RestTemplate streamRestTemplate = mock(RestTemplate.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    RetryAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class))
            .withBean("aiServiceRestTemplate", RestTemplate.class, () -> aiRestTemplate)
            .withBean("aiServiceStreamRestTemplate", RestTemplate.class, () -> streamRestTemplate)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(AIServiceClient.class)
            .withPropertyValues(
                    "ai-service.base-url=http://127.0.0.1:1",
                    "ai-service.internal-token=test-token",
                    "resilience4j.retry.instances.aiService.maxAttempts=2",
                    "resilience4j.retry.instances.aiService.waitDuration=1ms",
                    "resilience4j.retry.instances.aiService.retryExceptions[0]=" + BusinessException.class.getName(),
                    "resilience4j.circuitbreaker.instances.aiService.slidingWindowSize=2",
                    "resilience4j.circuitbreaker.instances.aiService.minimumNumberOfCalls=2");

    @Test
    void embedText_transportFailure_isRetriedBySpringAopAndFallsBack() {
        when(aiRestTemplate.postForEntity(anyString(), any(), eq(EmbeddingResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        contextRunner.run(context -> {
            AIServiceClient client = context.getBean(AIServiceClient.class);

            EmbeddingResponse response = client.embedText(EmbeddingRequest.builder()
                    .text("family memory")
                    .dimensions(1536)
                    .build());

            assertTrue(AopUtils.isAopProxy(client));
            assertFalse(response.isSuccess());
            verify(aiRestTemplate, times(2)).postForEntity(anyString(), any(), eq(EmbeddingResponse.class));
        });
    }
}
