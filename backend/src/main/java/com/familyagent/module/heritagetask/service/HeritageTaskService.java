package com.familyagent.module.heritagetask.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.service.DiaryEntryService;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.heritagetask.dto.CompleteHeritageTaskRequest;
import com.familyagent.module.heritagetask.dto.CreateHeritageTaskRequest;
import com.familyagent.module.heritagetask.entity.HeritageTask;
import com.familyagent.module.heritagetask.repository.HeritageTaskRepository;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeritageTaskService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final HeritageTaskRepository taskRepository;
    private final MemoryEntryRepository memoryRepository;
    private final FamilyService familyService;
    private final DiaryEntryService diaryEntryService;

    @Transactional
    public HeritageTask create(CreateHeritageTaskRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());
        MemoryEntry memory = null;
        if (request.getMemoryId() != null) {
            memory = memoryRepository.selectById(request.getMemoryId());
            if (memory == null || !request.getFamilyId().equals(memory.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(memory.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "关联经验不存在");
            }
        }

        HeritageTask task = new HeritageTask();
        task.setFamilyId(request.getFamilyId());
        task.setMemoryId(request.getMemoryId());
        task.setCreatedBy(userId);
        task.setTitle(request.getTitle().trim());
        task.setAction(request.getAction().trim());
        task.setTargetLabel(blankToNull(request.getTargetLabel()));
        task.setDueDate(request.getDueDate());
        task.setStatus(EntityStatus.PENDING.name());
        task.setMetadata(buildMetadata(request, memory));
        taskRepository.insert(task);
        return task;
    }

    public List<HeritageTask> listFamilyTasks(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return taskRepository.findByFamily(familyId, normalizeLimit(limit));
    }

    @Transactional
    public HeritageTask complete(Long id, CompleteHeritageTaskRequest request) {
        Long userId = CurrentUserGuard.currentUserId();
        HeritageTask task = taskRepository.selectById(id);
        if (task == null || EntityStatus.ARCHIVED.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        familyService.checkMembership(task.getFamilyId());
        if (!"DONE".equals(task.getStatus())) {
            task.setStatus("DONE");
            task.setCompletedBy(userId);
            task.setCompletedAt(LocalDateTime.now());
        }
        task.setCompletionNote(blankToNull(request.getCompletionNote()));
        taskRepository.updateById(task);

        if (task.getCompletionNote() != null) {
            CreateDiaryEntryRequest diaryRequest = new CreateDiaryEntryRequest();
            diaryRequest.setFamilyId(task.getFamilyId());
            diaryRequest.setTitle("完成家庭任务：" + task.getTitle());
            diaryRequest.setContent(buildCompletionDiary(task));
            diaryRequest.setEntryType("IMPORTANT_EVENT");
            diaryRequest.setVisibility(MemoryScope.FAMILY_VISIBLE.name());
            diaryRequest.setTags(List.of("家庭任务", "经验传承"));
            diaryRequest.setMetadata(buildCompletionMetadata(task));
            diaryEntryService.create(diaryRequest);
        }
        return task;
    }

    static Map<String, Object> buildCompletionMetadata(HeritageTask task) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "HERITAGE_TASK_COMPLETION");
        metadata.put("heritageTaskId", task.getId());
        if (task.getMemoryId() != null) {
            metadata.put("sourceMemoryId", task.getMemoryId());
        }
        return metadata;
    }

    private static Map<String, Object> buildMetadata(CreateHeritageTaskRequest request, MemoryEntry memory) {
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.putIfAbsent("source", "HERITAGE_TASK");
        if (memory != null) {
            metadata.put("sourceMemoryType", memory.getType());
            metadata.put("sourceMemorySummary", memory.getSummary());
        }
        return metadata;
    }

    private static String buildCompletionDiary(HeritageTask task) {
        return String.join("\n\n",
                "家庭任务：" + task.getTitle(),
                "任务内容：" + task.getAction(),
                task.getTargetLabel() == null ? "" : "适用对象：" + task.getTargetLabel(),
                "完成记录：" + task.getCompletionNote()
        ).trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
