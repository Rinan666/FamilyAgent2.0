package com.familyagent.infra.ai;

import com.familyagent.infra.ai.dto.AIHealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    public AIHealthResponse healthCheck() {
        try {
            AIHealthResponse response = restTemplate.getForObject(
                    support.url("/ai/health"),
                    AIHealthResponse.class);
            return response == null
                    ? AIHealthResponse.down(AIClientRequestSupport.ERROR_AI_SERVICE)
                    : response;
        } catch (Exception error) {
            log.error("AI service health check failed: errorType={}", error.getClass().getSimpleName());
            return AIHealthResponse.down(AIClientRequestSupport.ERROR_AI_SERVICE);
        }
    }
}
