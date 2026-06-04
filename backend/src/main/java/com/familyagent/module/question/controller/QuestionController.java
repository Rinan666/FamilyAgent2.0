package com.familyagent.module.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.response.Result;
import com.familyagent.module.question.entity.KnowledgePoint;
import com.familyagent.module.question.entity.Question;
import com.familyagent.module.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题库控制器
 */
@Tag(name = "题库管理")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    // ========== 知识点 ==========

    @Operation(summary = "获取知识点树(根节点)")
    @GetMapping("/knowledge-points/tree")
    public Result<List<KnowledgePoint>> getKnowledgeTree() {
        return Result.success(questionService.getKnowledgeTree());
    }

    @Operation(summary = "获取子知识点")
    @GetMapping("/knowledge-points/{parentId}/children")
    public Result<List<KnowledgePoint>> getChildKnowledgePoints(@PathVariable Long parentId) {
        return Result.success(questionService.getChildKnowledgePoints(parentId));
    }

    @Operation(summary = "获取知识点详情")
    @GetMapping("/knowledge-points/{id}")
    public Result<KnowledgePoint> getKnowledgePoint(@PathVariable Long id) {
        return Result.success(questionService.getKnowledgePoint(id));
    }

    // ========== 题目 ==========

    @Operation(summary = "获取题目详情")
    @GetMapping("/{id}")
    public Result<Question> getQuestion(@PathVariable Long id) {
        return Result.success(questionService.getQuestion(id));
    }

    @Operation(summary = "题目列表（分页）")
    @GetMapping
    public Result<PageResult<Question>> listQuestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Long kpId,
            @RequestParam(required = false) Integer difficulty) {
        Page<Question> pageResult = questionService.listQuestions(page, size, subject, kpId, difficulty);
        return Result.success(PageResult.of(
                pageResult.getRecords(), pageResult.getCurrent(),
                pageResult.getSize(), pageResult.getTotal()));
    }

    @Operation(summary = "抽题（按条件）")
    @PostMapping("/select")
    public Result<List<Question>> selectForTest(
            @RequestParam(required = false) Long kpId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(questionService.selectForTest(kpId, subject, difficulty, type, limit));
    }

    @Operation(summary = "自适应抽题（基于学力）")
    @PostMapping("/adaptive-select")
    public Result<List<Question>> selectAdaptive(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(questionService.selectAdaptive(userId, limit));
    }

    @Operation(summary = "获取错题")
    @GetMapping("/wrong")
    public Result<List<Question>> getWrongQuestions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(questionService.getWrongQuestions(userId, limit));
    }
}
