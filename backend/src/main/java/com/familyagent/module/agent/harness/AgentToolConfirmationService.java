package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.constant.AgentConfirmationDecision;
import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AgentToolConfirmationService {

    private static final int REQUEST_ID_LIMIT = 128;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final AgentToolConfirmationRecordRepository repository;
    private final AgentToolInputSummarizer inputSummarizer;
    private final AgentToolConfirmationPayloadCodec payloadCodec;

    public AgentToolConfirmationRecord createRequired(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            Object input) {
        AgentToolConfirmationRecord record = new AgentToolConfirmationRecord();
        record.setToolName(descriptor.name());
        record.setRunId(context.runId());
        record.setFamilyId(context.familyId());
        record.setViewerUserId(context.viewerUserId());
        record.setRequestId(inputSummarizer.trim(context.requestId(), REQUEST_ID_LIMIT));
        record.setSessionId(context.sessionId());
        record.setAgentMode(inputSummarizer.trim(context.agentMode(), 80));
        record.setSubject(inputSummarizer.trim(context.subject(), 200));
        record.setContextLabel(inputSummarizer.trim(context.contextLabel(), 200));
        record.setCompleteRunAfterTool(context.completeRunAfterTool());
        record.setInputSummary(inputSummarizer.summarize(input));
        String inputPayload = payloadCodec.encode(descriptor, input);
        record.setInputPayload(inputPayload);
        record.setStatus(AgentConfirmationStatus.REQUIRED.name());
        String idempotencyKey = idempotencyKey(context, descriptor, sha256(inputPayload));
        record.setIdempotencyKey(idempotencyKey);
        record.setExpiresAt(LocalDateTime.now(Clock.systemDefaultZone()).plus(DEFAULT_TTL));
        AgentToolConfirmationRecord existing = repository.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        try {
            repository.insert(record);
            return record;
        } catch (DuplicateKeyException conflict) {
            AgentToolConfirmationRecord concurrent = repository.selectByIdempotencyKey(idempotencyKey);
            if (concurrent != null) {
                return concurrent;
            }
            throw conflict;
        }
    }

    public AgentToolConfirmationRecord decide(
            Long confirmationId,
            Long viewerUserId,
            AgentConfirmationDecision decision) {
        AgentToolConfirmationRecord record = repository.selectById(confirmationId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent tool confirmation not found");
        }
        if (viewerUserId == null || !viewerUserId.equals(record.getViewerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent tool confirmation belongs to another user");
        }

        AgentConfirmationStatus current = AgentConfirmationStatus.valueOf(record.getStatus());
        if (current != AgentConfirmationStatus.REQUIRED) {
            return record;
        }

        LocalDateTime now = LocalDateTime.now(Clock.systemDefaultZone());
        if (!record.getExpiresAt().isAfter(now)) {
            return transition(record, AgentConfirmationStatus.EXPIRED, now);
        }

        AgentConfirmationStatus next = decision == AgentConfirmationDecision.APPROVE
                ? AgentConfirmationStatus.APPROVED
                : AgentConfirmationStatus.REJECTED;
        return transition(record, next, now);
    }

    private AgentToolConfirmationRecord transition(
            AgentToolConfirmationRecord record,
            AgentConfirmationStatus next,
            LocalDateTime decidedAt) {
        record.setStatus(next.name());
        record.setDecidedAt(decidedAt);
        repository.updateById(record);
        return record;
    }

    private String idempotencyKey(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            String inputSummary) {
        String seed = String.join("|",
                safe(context.requestId()),
                safe(context.familyId()),
                safe(context.viewerUserId()),
                safe(descriptor.name()),
                safe(inputSummary));
        return sha256(seed);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
