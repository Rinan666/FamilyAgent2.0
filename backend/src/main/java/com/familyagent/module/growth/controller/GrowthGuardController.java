package com.familyagent.module.growth.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.dto.CreateGrowthGuardReportRequest;
import com.familyagent.module.growth.dto.UpdateGrowthGuardStatusRequest;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.entity.GrowthGuardReport;
import com.familyagent.module.growth.service.GrowthGuardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Growth Guard")
@RestController
@RequestMapping("/api/growth-guards")
@RequiredArgsConstructor
public class GrowthGuardController {

    private final GrowthGuardService growthGuardService;

    @Operation(summary = "Create a growth guard record")
    @PostMapping
    public Result<GrowthGuardRecord> create(@Valid @RequestBody CreateGrowthGuardRecordRequest request) {
        return Result.success(growthGuardService.createRecord(request));
    }

    @Operation(summary = "List visible growth guard records in a family")
    @GetMapping("/family/{familyId}")
    public Result<List<GrowthGuardRecord>> listFamilyRecords(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.success(growthGuardService.listFamilyRecords(familyId, limit));
    }

    @Operation(summary = "Create a weekly growth guard report")
    @PostMapping("/reports")
    public Result<GrowthGuardReport> createReport(@Valid @RequestBody CreateGrowthGuardReportRequest request) {
        return Result.success(growthGuardService.createReport(request));
    }

    @Operation(summary = "List visible weekly growth guard reports in a family")
    @GetMapping("/reports/family/{familyId}")
    public Result<List<GrowthGuardReport>> listFamilyReports(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(growthGuardService.listFamilyReports(familyId, limit));
    }

    @Operation(summary = "Update follow-up status of a growth guard record")
    @PatchMapping("/{id}/follow-up-status")
    public Result<GrowthGuardRecord> updateFollowUpStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGrowthGuardStatusRequest request) {
        return Result.success(growthGuardService.updateFollowUpStatus(id, request.getFollowUpStatus()));
    }

    @Operation(summary = "Mark a growth guard record as stale")
    @PostMapping("/{id}/stale")
    public Result<GrowthGuardRecord> markStale(@PathVariable Long id) {
        return Result.success(growthGuardService.markRecordStale(id));
    }

    @Operation(summary = "Archive a growth guard record")
    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        growthGuardService.archiveRecord(id);
        return Result.success();
    }
}
