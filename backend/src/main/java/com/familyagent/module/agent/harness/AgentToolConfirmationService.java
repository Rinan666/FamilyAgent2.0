package com.familyagent.module.agent.harness;

import com.familyagent.module.agent.harness.constant.AgentConfirmationStatus;
import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;
import com.familyagent.module.agent.harness.repository.AgentToolConfirmationRecordRepository;
import lombok.RequiredArgsConstructor;
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

    public AgentToolConfirmationRecord createRequired(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            Object input) {
        AgentToolConfirmationRecord record = new AgentToolConfirmationRecord();
        record.setToolName(descriptor.name());
        record.setFamilyId(context.familyId());
        record.setViewerUserId(context.viewerUserId());
        record.setRequestId(inputSummarizer.trim(context.requestId(), REQUEST_ID_LIMIT));
        record.setInputSummary(inputSummarizer.summarize(input));
        record.setStatus(AgentConfirmationStatus.REQUIRED.name());
        record.setIdempotencyKey(idempotencyKey(context, descriptor, record.getInputSummary()));
        record.setExpiresAt(LocalDateTime.now(Clock.systemDefaultZone()).plus(DEFAULT_TTL));
        repository.insert(record);
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
