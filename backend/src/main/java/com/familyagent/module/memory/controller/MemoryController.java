package com.familyagent.module.memory.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.dto.CreateMemoryEntryRequest;
import com.familyagent.module.memory.dto.MemoryRecallRequest;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
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

    @Operation(summary = "Create a learning memory")
    @PostMapping
    public Result<MemoryEntry> create(@Valid @RequestBody CreateMemoryEntryRequest request) {
        return Result.success(memoryService.createMemory(request));
    }

    @Operation(summary = "List current user's learning memories")
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

    @Operation(summary = "Recall memories for tutor context")
    @PostMapping("/recall")
    public Result<List<MemoryEntry>> recall(@RequestBody(required = false) MemoryRecallRequest request) {
        if (request == null) {
            request = new MemoryRecallRequest();
        }
        int limit = request.getLimit() == null ? 8 : request.getLimit();
        return Result.success(memoryService.recall(request.getSubject(), request.getKnowledgePointId(), limit));
    }

    @Operation(summary = "Rebuild family memory embeddings")
    @PostMapping("/family/{familyId}/embeddings/rebuild")
    public Result<RebuildEmbeddingResponse> rebuildFamilyEmbeddings(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "200") int limit) {
        return Result.success(memoryEmbeddingService.rebuildFamilyEmbeddings(familyId, limit));
    }

    @Operation(summary = "Archive a memory")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        memoryService.archiveMemory(id);
        return Result.success();
    }
}
