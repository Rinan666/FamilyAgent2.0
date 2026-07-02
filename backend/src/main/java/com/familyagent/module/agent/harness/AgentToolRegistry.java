package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for all Spring-managed Agent tools.
 */
@Component
public class AgentToolRegistry {

    @Getter
    private final Map<String, AgentTool<?, ?>> tools;

    public AgentToolRegistry(List<AgentTool<?, ?>> tools) {
        this.tools = tools.stream()
                .collect(Collectors.toUnmodifiableMap(
                        tool -> normalize(tool.descriptor().name()),
                        Function.identity(),
                        (left, right) -> {
                            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                                    "Duplicate Agent tool: " + left.descriptor().name());
                        }));
    }

    public AgentTool<?, ?> require(String toolName) {
        AgentTool<?, ?> tool = tools.get(normalize(toolName));
        if (tool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent tool not found");
        }
        return tool;
    }

    public Collection<AgentToolDescriptor> descriptors() {
        return tools.values().stream()
                .map(AgentTool::descriptor)
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
