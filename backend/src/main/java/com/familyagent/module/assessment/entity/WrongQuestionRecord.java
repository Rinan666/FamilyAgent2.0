package com.familyagent.module.assessment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式错题记录。
 */
@Data
@TableName("wrong_question_records")
public class WrongQuestionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long familyId;
    private Long testRecordId;
    private Long questionId;
    private Long kpId;
    private String studentAnswer;
    private Double score;
    private Boolean correct;
    private String errorType;
    private String feedback;
    private String parentExplanation;
    private String nextSuggestion;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
