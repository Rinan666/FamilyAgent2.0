package com.familyagent.module.skillrun.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.service.SkillRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Skill Run Audit")
@RestController
@RequestMapping("/api/skill-runs")
@RequiredArgsConstructor
public class SkillRunController {

    private final SkillRunService skillRunService;

    @Operation(summary = "Create a skill run audit record")
    @PostMapping
    public Result<SkillRun> create(@Valid @RequestBody CreateSkillRunRequest request) {
        return Result.success(skillRunService.create(request));
    }

    @Operation(summary = "List skill run audit records in a family")
    @GetMapping("/family/{familyId}")
    public Result<List<SkillRun>> listFamilyRuns(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.success(skillRunService.listFamilyRuns(familyId, limit));
    }

    @Operation(summary = "Get a skill run audit record")
    @GetMapping("/{id}")
    public Result<SkillRun> get(@PathVariable Long id) {
        return Result.success(skillRunService.get(id));
    }

    @Operation(summary = "Update a skill run audit record")
    @PatchMapping("/{id}")
    public Result<SkillRun> update(
            @PathVariable Long id,
            @RequestBody UpdateSkillRunRequest request) {
        return Result.success(skillRunService.update(id, request));
    }
}
