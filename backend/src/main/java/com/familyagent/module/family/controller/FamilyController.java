package com.familyagent.module.family.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.family.dto.CreateFamilyRequest;
import com.familyagent.module.family.dto.CareAuthorizationVO;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.dto.FamilyRelationshipVO;
import com.familyagent.module.family.dto.UpsertCareAuthorizationRequest;
import com.familyagent.module.family.dto.UpsertFamilyRelationshipRequest;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.service.CareAuthorizationService;
import com.familyagent.module.family.service.FamilyRelationshipService;
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
    private final FamilyRelationshipService relationshipService;
    private final CareAuthorizationService careAuthorizationService;

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
    public Result<List<FamilyMemberVO>> getMembers(@PathVariable Long familyId) {
        return Result.success(familyService.getMembers(familyId));
    }

    @Operation(summary = "更新家族成员当前角色")
    @PutMapping("/{familyId}/members/{userId}/role")
    public Result<FamilyMemberVO> updateMemberRole(
            @PathVariable Long familyId,
            @PathVariable Long userId,
            @RequestParam String role) {
        return Result.success(familyService.updateMemberRole(familyId, userId, role));
    }

    @Operation(summary = "获取当前用户对家族成员的称呼")
    @GetMapping("/{familyId}/relationships/my-labels")
    public Result<List<FamilyRelationshipVO>> getMyRelationshipLabels(@PathVariable Long familyId) {
        return Result.success(relationshipService.listMyLabels(familyId));
    }

    @Operation(summary = "设置当前用户对某个家族成员的称呼")
    @PutMapping("/{familyId}/members/{targetUserId}/relationship")
    public Result<FamilyRelationshipVO> upsertRelationshipLabel(
            @PathVariable Long familyId,
            @PathVariable Long targetUserId,
            @Valid @RequestBody UpsertFamilyRelationshipRequest request) {
        return Result.success(relationshipService.upsertMyLabel(familyId, targetUserId, request));
    }

    @Operation(summary = "获取当前用户相关的照护授权")
    @GetMapping("/{familyId}/care-authorizations/my")
    public Result<List<CareAuthorizationVO>> getMyCareAuthorizations(@PathVariable Long familyId) {
        return Result.success(careAuthorizationService.listMyCareAuthorizations(familyId));
    }

    @Operation(summary = "设置成员照护授权")
    @PutMapping("/{familyId}/members/{subjectUserId}/caregivers/{caregiverUserId}")
    public Result<CareAuthorizationVO> upsertCareAuthorization(
            @PathVariable Long familyId,
            @PathVariable Long subjectUserId,
            @PathVariable Long caregiverUserId,
            @Valid @RequestBody UpsertCareAuthorizationRequest request) {
        return Result.success(careAuthorizationService.upsertAuthorization(
                familyId,
                subjectUserId,
                caregiverUserId,
                request));
    }
}
