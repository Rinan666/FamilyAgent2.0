package com.familyagent.infra.ai;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.dto.AIHealthResponse;
import com.familyagent.infra.ai.dto.AgentChatStreamPayload;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.infra.ai.dto.SaveMemoryPlanPayload;
import com.familyagent.infra.ai.dto.SaveMemoryPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.OutputStream;

/**
 * Compatibility facade for backend callers of the Python AI service.
 */
@Component
@RequiredArgsConstructor
public class AIServiceClient {

    private final SaveMemoryPlanClient saveMemoryPlanClient;
    private final AIChatStreamClient chatStreamClient;
    private final AIEmbeddingClient embeddingClient;
    private final AIHealthClient healthClient;
    private final AIClientRequestSupport requestSupport;

    public SaveMemoryPlanResponse planSaveMemory(SaveMemoryPlanPayload payload, String requestId) {
        try {
            return saveMemoryPlanClient.plan(payload, requestSupport.normalizeRequestId(requestId));
        } catch (AIServiceInputRejectedException error) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error.getMessage());
        }
    }

    public SaveMemoryPlanResponse planSaveMemory(
            SaveMemoryPlanPayload payload,
            String requestId,
            Long runId) {
        try {
            return saveMemoryPlanClient.plan(payload, requestSupport.normalizeRequestId(requestId), runId);
        } catch (AIServiceInputRejectedException error) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error.getMessage());
        }
    }

    public void proxyChatStream(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId) {
        chatStreamClient.proxyChatStream(request, downstream, authorization, requestId);
    }

    public void proxyChatStream(
            AgentChatStreamPayload request,
            OutputStream downstream,
            String authorization,
            String requestId,
            Long runId) {
        chatStreamClient.proxyChatStream(request, downstream, authorization, requestId, runId);
    }

    public EmbeddingResponse embedText(EmbeddingRequest request) {
        return embeddingClient.embedText(request);
    }

    public AIHealthResponse healthCheck() {
        return healthClient.healthCheck();
    }
}
