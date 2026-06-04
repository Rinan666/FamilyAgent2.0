package com.familyagent.module.assessment.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.service.AssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评估控制器
 */
@Tag(name = "学力评估")
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @Operation(summary = "获取用户学力档案")
    @GetMapping("/profiles/{userId}")
    public Result<List<AbilityProfile>> getUserProfiles(@PathVariable Long userId) {
        return Result.success(assessmentService.getUserProfiles(userId));
    }

    @Operation(summary = "获取最近发展区")
    @GetMapping("/zpd/{userId}")
    public Result<List<AbilityProfile>> getZPD(@PathVariable Long userId) {
        return Result.success(assessmentService.getZPD(userId));
    }

    @Operation(summary = "获取测试历史")
    @GetMapping("/history/{userId}")
    public Result<List<TestRecord>> getTestHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(assessmentService.getTestHistory(userId, limit));
    }
}
