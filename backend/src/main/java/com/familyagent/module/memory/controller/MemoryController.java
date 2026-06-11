package com.familyagent.module.memory.controller;

import com.familyagent.common.response.PageResult;
import com.familyagent.common.response.Result;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.dto.MemoryRecallRequest;
import com.familyagent.module.memory.dto.MemoryVoteRequest;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import com.familyagent.module.memory.service.MemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Memory")
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final AuthorizedMemoryRecallService authorizedMemoryRecallService;

    @Operation(summary = "Create a memory")
    @PostMapping
    public Result<MemoryEntry> create(@Valid @RequestBody CreateMemoryEntryRequest request) {
        return Result.success(memoryService.createMemory(request));
    }

    @Operation(summary = "List current user's memories")
    @GetMapping("/me")
    public Result<List<MemoryEntry>> listMyMemories(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(memoryService.listMyMemories(limit));
    }

    @Operation(summary = "Create a family heritage memory")
    @PostMapping("/family")
    public Result<MemoryEntry> createFamilyMemory(@Valid @RequestBody CreateFamilyMemoryRequest request) {
        return Result.success(memoryService.createFamilyMemory(request));
    }

    @Operation(summary = "List family heritage memories")
    @GetMapping("/family/{familyId}")
    public Result<List<MemoryEntry>> listFamilyMemories(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.success(memoryService.listFamilyMemories(familyId, limit));
    }

    @Operation(summary = "Search family heritage memories for member memory view")
    @GetMapping("/family/{familyId}/search")
    public Result<PageResult<MemoryEntry>> searchFamilyMemories(
            @PathVariable Long familyId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int pageSize) {
        return Result.success(memoryService.searchFamilyMemories(familyId, targetUserId, keyword, page, pageSize));
    }

    @Operation(summary = "Vote a family heritage memory")
    @PostMapping("/family/{memoryId}/vote")
    public Result<MemoryEntry> voteFamilyMemory(
            @PathVariable Long memoryId,
            @Valid @RequestBody MemoryVoteRequest request) {
        return Result.success(memoryService.voteFamilyMemory(memoryId, request.getVoteType()));
    }

    @Operation(summary = "Recall authorized family memories for RAG context")
    @PostMapping("/family/{familyId}/recall")
    public Result<AuthorizedMemoryRecallResult> recallFamily(
            @PathVariable Long familyId,
            @RequestBody(required = false) MemoryRecallRequest request) {
        if (request == null) {
            request = new MemoryRecallRequest();
        }
        Long viewerUserId = CurrentUserGuard.currentUserId();
        int diaryLimit = request.getDiaryLimit() == null ? normalizeRecallLimit(request.getLimit(), 8) : normalizeRecallLimit(request.getDiaryLimit(), 8);
        int memoryLimit = request.getMemoryLimit() == null ? normalizeRecallLimit(request.getLimit(), 8) : normalizeRecallLimit(request.getMemoryLimit(), 8);
        return Result.success(authorizedMemoryRecallService.recallForFamily(
                familyId,
                viewerUserId,
                request.getQuery(),
                request.getScene(),
                diaryLimit,
                memoryLimit));
    }

    @Operation(summary = "Rebuild family memory embeddings")
    @PostMapping("/family/{familyId}/embeddings/rebuild")
    public Result<RebuildEmbeddingResponse> rebuildFamilyEmbeddings(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "200") int limit) {
        return Result.success(memoryEmbeddingService.rebuildFamilyEmbeddings(familyId, limit));
    }

    @Operation(summary = "Rebuild family structured memory indexes")
    @PostMapping("/family/{familyId}/indexes/rebuild")
    public Result<RebuildEmbeddingResponse> rebuildFamilyIndexes(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "500") int limit) {
        return Result.success(memoryEmbeddingService.rebuildFamilyIndexes(familyId, limit));
    }

    @Operation(summary = "Archive a memory")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        memoryService.archiveMemory(id);
        return Result.success();
    }

    private static int normalizeRecallLimit(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, 20);
    }
}
