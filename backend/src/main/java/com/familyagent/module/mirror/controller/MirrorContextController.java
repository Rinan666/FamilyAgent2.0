package com.familyagent.module.mirror.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.mirror.dto.MirrorContextResponse;
import com.familyagent.module.mirror.service.MirrorContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mirror")
@RestController
@RequestMapping("/api/mirror")
@RequiredArgsConstructor
public class MirrorContextController {

    private final MirrorContextService mirrorContextService;

    @Operation(summary = "Get permission-filtered mirror context for a family member")
    @GetMapping("/families/{familyId}/members/{targetUserId}/context")
    public Result<MirrorContextResponse> getContext(
            @PathVariable Long familyId,
            @PathVariable Long targetUserId,
            @RequestParam(required = false) String query) {
        return Result.success(mirrorContextService.getContext(familyId, targetUserId, query));
    }
}
