package com.familyagent.module.diary.controller;

import com.familyagent.common.response.PageResult;
import com.familyagent.common.response.Result;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.dto.UpdateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.service.DiaryEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Diary")
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryEntryController {

    private final DiaryEntryService diaryService;

    @Operation(summary = "Create a family diary entry")
    @PostMapping
    public Result<DiaryEntry> create(@Valid @RequestBody CreateDiaryEntryRequest request) {
        return Result.success(diaryService.create(request));
    }

    @Operation(summary = "List visible diary entries in a family")
    @GetMapping("/family/{familyId}")
    public Result<List<DiaryEntry>> listFamilyEntries(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.success(diaryService.listFamilyEntries(familyId, limit));
    }

    @Operation(summary = "Search visible diary entries in a family for member memory view")
    @GetMapping("/family/{familyId}/search")
    public Result<PageResult<DiaryEntry>> searchFamilyEntries(
            @PathVariable Long familyId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int pageSize) {
        return Result.success(diaryService.searchFamilyEntries(familyId, targetUserId, keyword, page, pageSize));
    }

    @Operation(summary = "Update a diary entry")
    @PutMapping("/{id}")
    public Result<DiaryEntry> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDiaryEntryRequest request) {
        return Result.success(diaryService.update(id, request));
    }

    @Operation(summary = "Archive a diary entry")
    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        diaryService.archive(id);
        return Result.success();
    }
}
