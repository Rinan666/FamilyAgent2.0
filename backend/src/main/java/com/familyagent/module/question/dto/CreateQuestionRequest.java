package com.familyagent.module.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateQuestionRequest {

    private Long kpId;

    @NotBlank
    private String subject;

    private String grade;

    @NotBlank
    private String type;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer difficulty;

    @NotNull
    @Valid
    private QuestionContentPayload content;

    @NotNull
    @Valid
    private QuestionAnswerPayload answer;

    private List<String> tags;

    private String source = "MANUAL";

    @Data
    public static class QuestionContentPayload {
        @NotBlank
        private String stem;

        private List<String> options;

        private List<String> figures;
    }

    @Data
    public static class QuestionAnswerPayload {
        @NotBlank
        private String value;

        private List<String> steps;

        private String explanation;
    }
}
