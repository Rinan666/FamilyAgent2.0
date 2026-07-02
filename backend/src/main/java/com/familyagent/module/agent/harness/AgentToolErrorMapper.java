package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import org.springframework.stereotype.Component;

/**
 * Maps tool exceptions to stable harness result fields.
 */
@Component
public class AgentToolErrorMapper {

    public AgentToolCallStatus status(BusinessException exception) {
        if (exception.getCode() == ErrorCode.FORBIDDEN.getCode()) {
            return AgentToolCallStatus.DENIED;
        }
        return AgentToolCallStatus.FAILED;
    }

    public String errorCode(BusinessException exception) {
        if (exception.getCode() == AgentToolErrorCode.INVALID_INPUT.businessCode()) {
            return AgentToolErrorCode.INVALID_INPUT.code();
        }
        if (exception.getCode() == ErrorCode.FORBIDDEN.getCode()) {
            return AgentToolErrorCode.PERMISSION_DENIED.code();
        }
        return "BUSINESS_" + exception.getCode();
    }
}
