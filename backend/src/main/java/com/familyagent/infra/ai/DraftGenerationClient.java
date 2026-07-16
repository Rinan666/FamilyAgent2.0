package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.DraftGenerationResponse;
import com.familyagent.infra.ai.dto.OrganizeDraftPayload;
import com.familyagent.infra.ai.dto.OrganizeDraftResponse;
import com.familyagent.infra.ai.dto.PersonaMaterialDraftPayload;
import com.familyagent.infra.ai.dto.PersonaMaterialDraftResponse;
import com.familyagent.module.agent.constant.AgentDraftErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class DraftGenerationClient {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RUN_ID_HEADER = "X-Agent-Run-Id";
    private static final String ORGANIZE_OPERATION = "organize_draft";
    private static final String PERSONA_OPERATION = "persona_material_draft";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final MeterRegistry meterRegistry;

    public DraftGenerationClient(
            @Qualifier("aiServiceRestTemplate") RestTemplate restTemplate,
            @Value("${ai-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${ai-service.internal-token:}") String internalToken,
            MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.meterRegistry = meterRegistry;
    }

    @CircuitBreaker(name = "aiService")
    @Retry(name = "aiService", fallbackMethod = "fallbackOrganizeDraft")
    public OrganizeDraftResponse organize(
            OrganizeDraftPayload payload,
            String requestId,
            Long runId) {
        return post(
                "/ai/memory/organize-draft",
                payload,
                requestId,
                runId,
                ORGANIZE_OPERATION,
                OrganizeDraftResponse.class,
                OrganizeDraftResponse::failure);
    }

    @CircuitBreaker(name = "aiService")
    @Retry(name = "aiService", fallbackMethod = "fallbackPersonaMaterialDraft")
    public PersonaMaterialDraftResponse organizePersonaMaterial(
            PersonaMaterialDraftPayload payload,
            String requestId,
            Long runId) {
        return post(
                "/ai/memory/persona-material-draft",
                payload,
                requestId,
                runId,
                PERSONA_OPERATION,
                PersonaMaterialDraftResponse.class,
                PersonaMaterialDraftResponse::failure);
    }

    private <P, R extends DraftGenerationResponse<?>> R post(
            String path,
            P payload,
            String requestId,
            Long runId,
            String operation,
            Class<R> responseType,
            Function<AgentDraftErrorCode, R> failureFactory) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<R> response = restTemplate.postForEntity(
                    baseUrl + path,
                    new HttpEntity<>(payload, headers(requestId, runId)),
                    responseType);
            R body = response.getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service returned an empty response");
            }
            record(operation, body.isSuccess(), body.getErrorCode(), elapsed(startedAt));
            return body;
        } catch (HttpStatusCodeException error) {
            AgentDraftErrorCode errorCode = httpErrorCode(error.getStatusCode().value());
            record(operation, false, errorCode.name(), elapsed(startedAt));
            if (errorCode == AgentDraftErrorCode.AI_INPUT_REJECTED
                    || errorCode == AgentDraftErrorCode.AI_RATE_LIMITED) {
                return failureFactory.apply(errorCode);
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            record(operation, false, AgentDraftErrorCode.AI_SERVICE_ERROR.name(), elapsed(startedAt));
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI service unavailable");
        }
    }

    private HttpHeaders headers(String requestId, Long runId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(REQUEST_ID_HEADER, requestId);
        if (runId != null) {
            headers.set(RUN_ID_HEADER, String.valueOf(runId));
        }
        if (internalToken != null && !internalToken.isBlank()) {
            headers.set(INTERNAL_SERVICE_TOKEN_HEADER, internalToken);
        }
        return headers;
    }

    private AgentDraftErrorCode httpErrorCode(int status) {
        if (status == 400 || status == 422) {
            return AgentDraftErrorCode.AI_INPUT_REJECTED;
        }
        if (status == 429) {
            return AgentDraftErrorCode.AI_RATE_LIMITED;
        }
        return AgentDraftErrorCode.AI_SERVICE_ERROR;
    }

    private OrganizeDraftResponse fallbackOrganizeDraft(
            OrganizeDraftPayload payload,
            String requestId,
            Long runId,
            Exception error) {
        log.warn("Organize-draft fallback triggered: requestId={}, runId={}, errorType={}",
                requestId, runId, error.getClass().getSimpleName());
        return OrganizeDraftResponse.failure(AgentDraftErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private PersonaMaterialDraftResponse fallbackPersonaMaterialDraft(
            PersonaMaterialDraftPayload payload,
            String requestId,
            Long runId,
            Exception error) {
        log.warn("Persona-material fallback triggered: requestId={}, runId={}, errorType={}",
                requestId, runId, error.getClass().getSimpleName());
        return PersonaMaterialDraftResponse.failure(AgentDraftErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private void record(String operation, boolean success, String errorCode, Duration duration) {
        meterRegistry.timer(
                "familyagent.ai.client.request",
                "operation", operation,
                "success", Boolean.toString(success),
                "errorCode", errorCode == null || errorCode.isBlank() ? "NONE" : errorCode,
                "provider", "none",
                "model", "none",
                "degraded", "false"
        ).record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }
}
