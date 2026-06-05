package com.familyagent.module.assessment.dto;

import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.question.entity.Question;
import lombok.Data;

import java.util.List;

/**
 * 测试记录详情视图。
 */
@Data
public class TestRecordDetailVO {

    private TestRecord record;
    private List<Item> items;

    @Data
    public static class Item {
        private Long questionId;
        private Long kpId;
        private Question question;
        private String studentAnswer;
        private Object correctAnswer;
        private Double score;
        private Boolean correct;
        private Integer timeSpent;
        private Boolean wrong;
        private Long wrongRecordId;
        private String wrongStatus;
        private String errorType;
        private String feedback;
        private String parentExplanation;
        private String nextSuggestion;
    }
}
