package com.familyagent.module.family.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.family.dto.CreatePersonaMemberRequest;
import com.familyagent.module.family.dto.DeletePersonaMemberRequest;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.dto.UpdatePersonaMemberRequest;
import com.familyagent.module.family.service.FamilyPersonaMemberCommandService;
import com.familyagent.module.family.service.FamilyPersonaMemberQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "精神成员管理")
@RestController
@RequestMapping("/api/families/{familyId}/persona-members")
@RequiredArgsConstructor
public class FamilyPersonaMemberController {

    private final FamilyPersonaMemberCommandService commandService;
    private final FamilyPersonaMemberQueryService queryService;

    @Operation(summary = "获取家族精神成员列表")
    @GetMapping
    public Result<List<PersonaMemberVO>> list(@PathVariable Long familyId) {
        return Result.success(queryService.listByFamily(familyId));
    }

    @Operation(summary = "获取精神成员详情")
    @GetMapping("/{personaId}")
    public Result<PersonaMemberVO> get(@PathVariable Long familyId, @PathVariable Long personaId) {
        return Result.success(queryService.getById(familyId, personaId));
    }

    @Operation(summary = "创建精神成员")
    @PostMapping
    public Result<PersonaMemberVO> create(
            @PathVariable Long familyId,
            @Valid @RequestBody CreatePersonaMemberRequest request) {
        return Result.success(commandService.create(familyId, request));
    }

    @Operation(summary = "编辑精神成员")
    @PutMapping("/{personaId}")
    public Result<PersonaMemberVO> update(
            @PathVariable Long familyId,
            @PathVariable Long personaId,
            @Valid @RequestBody UpdatePersonaMemberRequest request) {
        return Result.success(commandService.update(familyId, personaId, request));
    }

    @Operation(summary = "删除精神成员（需输入确认词）")
    @DeleteMapping("/{personaId}")
    public Result<Void> delete(
            @PathVariable Long familyId,
            @PathVariable Long personaId,
            @Valid @RequestBody DeletePersonaMemberRequest request) {
        commandService.delete(familyId, personaId, request);
        return Result.success();
    }
}
