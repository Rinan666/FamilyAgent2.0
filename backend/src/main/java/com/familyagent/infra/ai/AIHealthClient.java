package com.familyagent.infra.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class AIHealthClient {

    private final RestTemplate restTemplate;
    private final AIClientRequestSupport support;

    public AIHealthClient(
            @Qualifier("aiServiceRestTemplate") RestTemplate restTemplate,
            AIClientRequestSupport support) {
        this.restTemplate = restTemplate;
        this.support = support;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> healthCheck() {
        try {
            return restTemplate.getForObject(support.url("/ai/health"), Map.class);
        } catch (Exception error) {
            log.error("AI service health check failed: errorType={}", error.getClass().getSimpleName());
            return Map.of("status", "DOWN", "errorCode", AIClientRequestSupport.ERROR_AI_SERVICE);
        }
    }
}
