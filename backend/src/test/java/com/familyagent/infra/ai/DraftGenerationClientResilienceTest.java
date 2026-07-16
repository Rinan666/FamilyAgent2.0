package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.infra.ai.dto.OrganizeDraftPayload;
import com.familyagent.infra.ai.dto.OrganizeDraftResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftGenerationClientResilienceTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    RetryAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class))
            .withBean("aiServiceRestTemplate", RestTemplate.class, () -> restTemplate)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(DraftGenerationClient.class)
            .withPropertyValues(
                    "ai-service.base-url=http://127.0.0.1:1",
                    "ai-service.internal-token=test-token",
                    "resilience4j.retry.instances.aiService.maxAttempts=2",
                    "resilience4j.retry.instances.aiService.waitDuration=1ms",
                    "resilience4j.retry.instances.aiService.retryExceptions[0]=" + BusinessException.class.getName(),
                    "resilience4j.circuitbreaker.instances.aiService.slidingWindowSize=2",
                    "resilience4j.circuitbreaker.instances.aiService.minimumNumberOfCalls=2");

    @Test
    void transportFailureIsRetriedAndReturnsExplicitUnavailableResponse() {
        when(restTemplate.postForEntity(anyString(), any(), eq(OrganizeDraftResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        contextRunner.run(context -> {
            DraftGenerationClient client = context.getBean(DraftGenerationClient.class);

            OrganizeDraftResponse response = client.organize(
                    new OrganizeDraftPayload("content", "DIARY", "", "", "", ""),
                    "request-1",
                    91L);

            assertTrue(AopUtils.isAopProxy(client));
            assertFalse(response.isSuccess());
            assertEquals("AI_SERVICE_UNAVAILABLE", response.getErrorCode());
            verify(restTemplate, times(2)).postForEntity(
                    anyString(),
                    any(),
                    eq(OrganizeDraftResponse.class));
        });
    }
}
