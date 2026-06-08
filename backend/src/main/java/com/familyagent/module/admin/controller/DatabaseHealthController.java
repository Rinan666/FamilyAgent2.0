package com.familyagent.module.admin.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.admin.dto.DatabaseHealthResponse;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.admin.service.DatabaseHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "Diagnose memory recall as a family member")
    @PostMapping("/memory-recall-diagnostic")
    public Result<MemoryRecallDiagnosticResponse> diagnoseMemoryRecall(@RequestBody MemoryRecallDiagnosticRequest request) {
        return Result.success(databaseHealthService.diagnoseMemoryRecall(request));
    }
}
