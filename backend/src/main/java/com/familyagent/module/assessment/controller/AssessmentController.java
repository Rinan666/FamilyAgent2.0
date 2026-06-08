package com.familyagent.module.assessment.controller;

import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.assessment.dto.SubmitTestRequest;
import com.familyagent.module.assessment.dto.TestRecordDetailVO;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.service.AssessmentService;
import com.familyagent.module.family.service.FamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
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
    private final FamilyService familyService;

    @Operation(summary = "获取用户学力档案")
    @GetMapping("/profiles/me")
    public Result<List<AbilityProfile>> getMyProfiles() {
        return Result.success(assessmentService.getUserProfiles(CurrentUserGuard.currentUserId()));
    }

    @Operation(summary = "获取用户学力档案")
    @GetMapping("/profiles/{userId}")
    public Result<List<AbilityProfile>> getUserProfiles(@PathVariable Long userId) {
        familyService.checkCanViewLearningReport(userId);
        return Result.success(assessmentService.getUserProfiles(userId));
    }

    @Operation(summary = "获取最近发展区")
    @GetMapping("/zpd/me")
    public Result<List<AbilityProfile>> getMyZPD() {
        return Result.success(assessmentService.getZPD(CurrentUserGuard.currentUserId()));
    }

    @Operation(summary = "获取最近发展区")
    @GetMapping("/zpd/{userId}")
    public Result<List<AbilityProfile>> getZPD(@PathVariable Long userId) {
        familyService.checkCanViewLearningReport(userId);
        return Result.success(assessmentService.getZPD(userId));
    }

    @Operation(summary = "获取测试历史")
    @GetMapping("/history/me")
    public Result<List<TestRecord>> getMyTestHistory(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(assessmentService.getTestHistory(CurrentUserGuard.currentUserId(), limit));
    }

    @Operation(summary = "获取测试历史")
    @GetMapping("/history/{userId}")
    public Result<List<TestRecord>> getTestHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        familyService.checkCanViewLearningReport(userId);
        return Result.success(assessmentService.getTestHistory(userId, limit));
    }

    @Operation(summary = "提交测试结果并更新学力档案")
    @PostMapping("/tests")
    public Result<TestRecord> submitTest(@Valid @RequestBody SubmitTestRequest request) {
        request.setUserId(CurrentUserGuard.currentUserId());
        return Result.success(assessmentService.submitTest(request));
    }

    @Operation(summary = "获取测试记录详情")
    @GetMapping("/tests/{id}/detail")
    public Result<TestRecordDetailVO> getMyTestRecordDetail(@PathVariable Long id) {
        return Result.success(assessmentService.getTestRecordDetail(CurrentUserGuard.currentUserId(), id));
    }

    @Operation(summary = "获取指定学习者测试记录详情")
    @GetMapping("/users/{userId}/tests/{id}/detail")
    public Result<TestRecordDetailVO> getUserTestRecordDetail(
            @PathVariable Long userId,
            @PathVariable Long id) {
        familyService.checkCanViewLearningReport(userId);
        return Result.success(assessmentService.getTestRecordDetail(userId, id));
    }
}
