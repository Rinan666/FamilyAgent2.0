package com.familyagent.module.agent.harness;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class AgentReplayPrivacyFilter {

    private static final Pattern SAFE_INPUT_TYPE = Pattern.compile("inputType=[A-Za-z0-9_.$]{1,120}");

    public String requestRef(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestId.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public String inputType(String inputSummary) {
        if (inputSummary == null || inputSummary.isBlank()) {
            return null;
        }
        String normalized = inputSummary.trim();
        return SAFE_INPUT_TYPE.matcher(normalized).matches()
                ? normalized
                : "inputType=REDACTED";
    }
}
