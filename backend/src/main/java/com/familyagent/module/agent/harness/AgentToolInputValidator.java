package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import org.springframework.stereotype.Component;

/**
 * Validates runtime tool input against its declared type.
 */
@Component
public class AgentToolInputValidator {

    public void validate(AgentTool<?, ?> tool, Object input) {
        if (input == null || !tool.inputType().isInstance(input)) {
            throw new BusinessException(
                    AgentToolErrorCode.INVALID_INPUT.businessCode(),
                    "Agent tool input type is invalid");
        }
    }
}
