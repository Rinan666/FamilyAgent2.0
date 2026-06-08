package com.familyagent.module.heritagetask.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.heritagetask.dto.CompleteHeritageTaskRequest;
import com.familyagent.module.heritagetask.dto.CreateHeritageTaskRequest;
import com.familyagent.module.heritagetask.entity.HeritageTask;
import com.familyagent.module.heritagetask.service.HeritageTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Heritage Task")
@RestController
@RequestMapping("/api/heritage-tasks")
@RequiredArgsConstructor
public class HeritageTaskController {

    private final HeritageTaskService heritageTaskService;

    @Operation(summary = "Create a heritage task from family experience")
    @PostMapping
    public Result<HeritageTask> create(@Valid @RequestBody CreateHeritageTaskRequest request) {
        return Result.success(heritageTaskService.create(request));
    }

    @Operation(summary = "List family heritage tasks")
    @GetMapping("/family/{familyId}")
    public Result<List<HeritageTask>> listFamilyTasks(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(heritageTaskService.listFamilyTasks(familyId, limit));
    }

    @Operation(summary = "Complete a heritage task and archive completion note as diary")
    @PatchMapping("/{id}/complete")
    public Result<HeritageTask> complete(
            @PathVariable Long id,
            @Valid @RequestBody CompleteHeritageTaskRequest request) {
        return Result.success(heritageTaskService.complete(id, request));
    }
}
