package com.familyagent.module.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学力档案实体
 */
@Data
@TableName("ability_profiles")
public class AbilityProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long kpId;
    private Double masteryProbability;
    private Integer totalAttempts;
    private Integer correctAttempts;
    private Integer consecutiveCorrect;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime lastCorrectAt;
    private LocalDateTime updatedAt;
}
