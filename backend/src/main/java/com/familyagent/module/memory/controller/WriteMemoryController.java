package com.familyagent.module.memory.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.memory.dto.WriteMemoryRequest;
import com.familyagent.module.memory.dto.WriteMemoryResult;
import com.familyagent.module.memory.service.WriteMemoryCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Write Memory")
@RestController
@RequestMapping("/api/memories/write")
@RequiredArgsConstructor
public class WriteMemoryController {

    private final WriteMemoryCommandService writeMemoryCommandService;

    @Operation(summary = "Create a unified write entry")
    @PostMapping
    public Result<WriteMemoryResult> write(@Valid @RequestBody WriteMemoryRequest request) {
        return Result.success(writeMemoryCommandService.write(request));
    }
}
