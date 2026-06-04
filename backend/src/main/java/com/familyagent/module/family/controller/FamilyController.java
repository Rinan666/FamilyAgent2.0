package com.familyagent.module.family.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.family.dto.CreateFamilyRequest;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.service.FamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 家族控制器
 */
@Tag(name = "家族管理")
@RestController
@RequestMapping("/api/families")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    @Operation(summary = "创建家族")
    @PostMapping
    public Result<Family> createFamily(@Valid @RequestBody CreateFamilyRequest request) {
        return Result.success(familyService.createFamily(request));
    }

    @Operation(summary = "通过邀请码加入家族")
    @PostMapping("/join")
    public Result<FamilyMember> joinFamily(@RequestParam String inviteCode) {
        return Result.success(familyService.joinFamily(inviteCode));
    }

    @Operation(summary = "获取我的家族列表")
    @GetMapping("/my")
    public Result<List<Family>> getMyFamilies() {
        return Result.success(familyService.getMyFamilies());
    }

    @Operation(summary = "获取家族详情")
    @GetMapping("/{familyId}")
    public Result<Family> getFamily(@PathVariable Long familyId) {
        return Result.success(familyService.getFamily(familyId));
    }

    @Operation(summary = "获取家族成员列表")
    @GetMapping("/{familyId}/members")
    public Result<List<FamilyMember>> getMembers(@PathVariable Long familyId) {
        return Result.success(familyService.getMembers(familyId));
    }
}
