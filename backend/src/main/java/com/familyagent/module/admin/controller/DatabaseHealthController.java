package com.familyagent.module.admin.controller;

import com.familyagent.common.response.PageResult;
import com.familyagent.common.response.Result;
import com.familyagent.module.admin.dto.AdminUserSummary;
import com.familyagent.module.admin.dto.DatabaseHealthResponse;
import com.familyagent.module.admin.dto.FamilyDatabaseSummary;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.admin.service.DatabaseHealthService;
import com.familyagent.module.family.dto.FamilyMemberVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Database")
@RestController
@RequestMapping("/api/admin/database")
@RequiredArgsConstructor
public class DatabaseHealthController {

    private final DatabaseHealthService databaseHealthService;

    @Operation(summary = "Get database health summary")
    @GetMapping("/health")
    public Result<DatabaseHealthResponse> getHealth() {
        return Result.success(databaseHealthService.getHealth());
    }

    @Operation(summary = "List users with IDs as platform admin")
    @GetMapping("/users")
    public Result<PageResult<AdminUserSummary>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(databaseHealthService.searchUsers(keyword, page, pageSize));
    }

    @Operation(summary = "List family database summaries as platform admin")
    @GetMapping("/families")
    public Result<PageResult<FamilyDatabaseSummary>> listFamilies(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(databaseHealthService.searchFamilies(keyword, page, pageSize));
    }

    @Operation(summary = "List family members as platform admin")
    @GetMapping("/families/{familyId}/members")
    public Result<List<FamilyMemberVO>> listFamilyMembers(@PathVariable Long familyId) {
        return Result.success(databaseHealthService.listFamilyMembers(familyId));
    }

    @Operation(summary = "Delete a user and related records as platform admin")
    @DeleteMapping("/users/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        databaseHealthService.deleteUser(userId);
        return Result.success();
    }

    @Operation(summary = "Diagnose memory recall as a family member")
    @PostMapping("/memory-recall-diagnostic")
    public Result<MemoryRecallDiagnosticResponse> diagnoseMemoryRecall(@RequestBody MemoryRecallDiagnosticRequest request) {
        return Result.success(databaseHealthService.diagnoseMemoryRecall(request));
    }
}
