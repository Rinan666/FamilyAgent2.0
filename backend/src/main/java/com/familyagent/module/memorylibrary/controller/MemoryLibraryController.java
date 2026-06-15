package com.familyagent.module.memorylibrary.controller;

import com.familyagent.common.response.PageResult;
import com.familyagent.common.response.Result;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryClassicalizeRequest;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryUpdateRequest;
import com.familyagent.module.memorylibrary.service.MemoryLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Memory Library")
@RestController
@RequestMapping("/api/memory-library")
@RequiredArgsConstructor
public class MemoryLibraryController {

    private final MemoryLibraryService memoryLibraryService;

    @Operation(summary = "Search visible family memory library items")
    @GetMapping("/search")
    public Result<PageResult<MemoryLibraryItem>> search(@ModelAttribute MemoryLibrarySearchRequest request) {
        return Result.success(memoryLibraryService.search(request));
    }

    @Operation(summary = "Search archived family memory library items")
    @GetMapping("/archived")
    public Result<PageResult<MemoryLibraryItem>> archived(@ModelAttribute MemoryLibrarySearchRequest request) {
        return Result.success(memoryLibraryService.archived(request));
    }

    @Operation(summary = "Rewrite one family experience item into classical Chinese after preview confirmation")
    @PostMapping("/classicalize")
    public Result<Void> classicalize(@Valid @RequestBody MemoryLibraryClassicalizeRequest request) {
        memoryLibraryService.classicalizeLibraryItem(
                request.getFamilyId(),
                request.getItemId(),
                request.getClassicalText(),
                request.getPlainSummary(),
                request.getStyleNote());
        return Result.success();
    }

    @Operation(summary = "Edit a memory library item after manual review")
    @PutMapping("/item")
    public Result<Void> update(@Valid @RequestBody MemoryLibraryUpdateRequest request) {
        memoryLibraryService.updateLibraryItem(request);
        return Result.success();
    }

    @Operation(summary = "Archive a memory library item after manual review")
    @PostMapping("/archive")
    public Result<Void> archive(
            @RequestParam Long familyId,
            @RequestParam String itemId) {
        memoryLibraryService.archiveLibraryItem(familyId, itemId);
        return Result.success();
    }

    @Operation(summary = "Restore an archived memory library item after manual review")
    @PostMapping("/restore")
    public Result<Void> restore(
            @RequestParam Long familyId,
            @RequestParam String itemId) {
        memoryLibraryService.restoreLibraryItem(familyId, itemId);
        return Result.success();
    }

    @Operation(summary = "Permanently delete an archived memory library item")
    @DeleteMapping("/archived")
    public Result<Void> deleteArchived(
            @RequestParam Long familyId,
            @RequestParam String itemId) {
        memoryLibraryService.deleteArchivedLibraryItem(familyId, itemId);
        return Result.success();
    }
}
