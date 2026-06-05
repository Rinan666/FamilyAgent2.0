package com.familyagent.module.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 提交测试结果请求
 */
@Data
public class SubmitTestRequest {

    private Long userId;

    private Long familyId;

    @NotEmpty
    @Valid
    private List<TestQuestionResult> results;

    private Integer totalTime;

    private String source = "GENERATED_TEST";

    @Data
    public static class TestQuestionResult {
        @NotNull
        private Long questionId;

        @NotNull
        private Long kpId;

        private String answer;

        private Double score;

        private Boolean correct;

        private String errorType;

        private String feedback;

        private String parentExplanation;

        private String nextSuggestion;

        private Integer timeSpent;
    }
}
